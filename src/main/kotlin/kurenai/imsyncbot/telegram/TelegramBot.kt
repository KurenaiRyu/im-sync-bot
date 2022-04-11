package kurenai.imsyncbot.telegram

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ContextHolder.tdClient
import kurenai.imsyncbot.ContextHolder.telegramBot
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.command.DelegatingCommand
import kurenai.imsyncbot.config.BotProperties
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.qq.QQBotClient
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBotProperties.Companion.DEFAULT_BASE_URL
import moe.kurenai.tdlight.AbstractUpdateSubscriber
import moe.kurenai.tdlight.LongPollingTelegramBot
import moe.kurenai.tdlight.client.TDLightClient
import moe.kurenai.tdlight.exception.TelegramApiRequestException
import moe.kurenai.tdlight.model.ResponseWrapper
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardButton
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardMarkup
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.Request
import moe.kurenai.tdlight.request.message.SendMessage
import moe.kurenai.tdlight.util.DefaultMapper.MAPPER
import mu.KotlinLogging
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import java.net.URI
import java.util.*
import java.util.concurrent.*
import java.util.function.Function

/**
 * 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */
@Component
class TelegramBot(
    private val telegramBotProperties: TelegramBotProperties, //初始化时处理器列表
    private val botProperties: BotProperties,
    private val handlerHolder: HandlerHolder,
    private val callbacks: List<Callback>,
    private val cacheService: CacheService,
    private val privateChatHandler: PrivateChatHandler,
    private val qqBotClient: QQBotClient
) : AbstractUpdateSubscriber(), InitializingBean {

    val nextMsgUpdate: ConcurrentHashMap<Long, Update> = ConcurrentHashMap()
    val nextMsgLock: ConcurrentHashMap<Long, Object> = ConcurrentHashMap()
    val username: String = telegramBotProperties.username
    val token: String = telegramBotProperties.token

    private val log = KotlinLogging.logger {}
    private lateinit var bot: LongPollingTelegramBot

    private val pool = ThreadPoolExecutor(
        1, 1, 1L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(300)
    ) { r ->
        val t = Thread(
            Thread.currentThread().threadGroup, r,
            "telegram-bot",
            0
        )
        if (t.isDaemon) t.isDaemon = false
        if (t.priority != Thread.NORM_PRIORITY) t.priority = Thread.NORM_PRIORITY
        t
    }
    private val scope = pool.asCoroutineDispatcher()

    /**
     * Invoked by the containing `BeanFactory` after it has set all bean properties
     * and satisfied [BeanFactoryAware], `ApplicationContextAware` etc.
     *
     * This method allows the bean instance to perform validation of its overall
     * configuration and final initialization when all bean properties have been set.
     * @throws Exception in the event of misconfiguration (such as failure to set an
     * essential property) or if initialization fails for any other reason
     */
    override fun afterPropertiesSet() {
//        GetChatMember(GroupConfig.tgQQ[0])

        val baseUrl = if (telegramBotProperties.baseUrl == DEFAULT_BASE_URL) {
            DEFAULT_BASE_URL
        } else {
            val uri = URI(telegramBotProperties.baseUrl)
            if (uri.host == "api.telegram.org") DEFAULT_BASE_URL
            else if (uri.path == "/bot") telegramBotProperties.baseUrl.replace("/bot", "")
            else telegramBotProperties.baseUrl
        }

        log.debug { "Telegram base url: $baseUrl" }
        tdClient = TDLightClient(baseUrl, telegramBotProperties.token, isUserMode = false, isDebugEnabled = false)
        qqBotClient.startCountDown.await()
        bot = LongPollingTelegramBot(listOf(this), tdClient)
        telegramBot = this
        log.info { "Started telegram-bot $username" }
    }

    override fun onComplete0() {
    }

    override fun onError0(e: Throwable) {
    }

    override fun onNext0(update: Update) {
        CoroutineScope(scope).launch {
            try {
                onUpdateReceivedSuspend(update)
            } catch (e: Exception) {
                reportError(update, e)
            }
        }
    }

    override fun onSubscribe0() {
    }

    suspend fun onUpdateReceivedSuspend(update: Update) {
        log.debug("onUpdateReceived: {}", MAPPER.writeValueAsString(update))
        val message = update.message ?: update.editedMessage ?: update.callbackQuery?.message
        if (message == null) {
            log.debug { "No message" }
            return
        }

        if (botProperties.ban.member.contains(message.from?.id)) {
            log.debug("Ignore this message by ban member [${message.from!!.id}]")
            return
        }
        if (botProperties.ban.group.contains(message.chat.id)) {
            log.debug("Ignore this message by ban group [${message.chatId}]")
            return
        }

        if (message.isUserMessage()) {
            //TODO 还需要加入之前的用户，不然别的用户发送信息则会出问题
            nextMsgLock.remove(message.chat.id)?.let {
                nextMsgUpdate.putIfAbsent(message.chat.id, update)
                synchronized(it) {
                    it.notify()
                }
            }
        }

        if (update.hasCallbackQuery()) {
            try {
                for (callback in callbacks) {
                    if (callback.match(update) && callback.handle(update, message) == Callback.END) {
                        return
                    }
                }
            } catch (e: Exception) {
                log.error(e) { e.message }
                reportError(update, e, "执行回调失败", false)
            }
        }

        if (message.isCommand()) {
            DelegatingCommand.execute(update, message)
        } else if (message.chat.id == privateChatHandler.privateChat) {
            privateChatHandler.onPrivateChat(update)
        } else if ((message.isGroupMessage() || message.isSuperGroupMessage())) {
            if (update.hasMessage()) {
                for (handler in handlerHolder.currentTgHandlerList) {
                    if (handler.onMessage(message) == END) break
                }
            } else if (update.hasEditedMessage()) {
                for (handler in handlerHolder.currentTgHandlerList) {
                    if (handler.onEditMessage(update.editedMessage!!) == END) break
                }
            }
        }
    }

    suspend fun reportError(update: Update, e: Throwable, topic: String = "转发失败", canRetry: Boolean = true) {
        log.error(e) { e.message }
        try {
            val message = update.message ?: update.editedMessage ?: update.callbackQuery?.message ?: return

            //pm master
//            val simpleMsg = "#转发失败\n${e.message}\n\nhttps://t.me/c/${
//                msgChatId.let { id ->
//                    if (id.startsWith("-100")) {
//                        id.substring(4)
//                    } else id
//                }
//            }/$msgId"
//            configService.get(BotConfigKey.MASTER_CHAT_ID)?.let {
//                val recMsgId = send(SendMessage(it, simpleMsg)).messageId
//                send(EditMessageText.builder().chatId(it).messageId(recMsgId).text("$simpleMsg\n\n${mapper.writeValueAsString(update)}").build())
//            }

            SendMessage(message.chatId, "#$topic\n${e.message}").apply {
                this.replyToMessageId = message.messageId
                this.replyMarkup =
                    InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("重试").apply { this.callbackData = "retry" })))
            }.send()
            cacheService.cache(message)
        } catch (e: Exception) {
            log.error(e) { e.message }
        }
    }
}

val log: Logger = LogManager.getLogger()
val mainClientLock = Object()

fun <T> Request<ResponseWrapper<T>>.send(): CompletableFuture<out T> {
    return tdClient.send(this).handle { result, case ->
        return@handle case?.let {
            try {
                CompletableFuture.completedFuture(this.sendSync())
            } catch (e: Exception) {
                log.error(e.message, e)
                CompletableFuture.failedFuture(e)
            }
        } ?: CompletableFuture.completedFuture(result)
    }.thenCompose(Function.identity())
}

fun <T> Request<ResponseWrapper<T>>.sendSync(): T {
    synchronized(mainClientLock) {
        while (true) {
            try {
                return tdClient.sendSync(this)
            } catch (e: TelegramApiRequestException) {
                val retryAfter = e.response?.parameters?.retryAfter
                if (retryAfter != null) {
                    log.info("Wait for ${retryAfter}s")
                    mainClientLock.wait(retryAfter.toLong() * 1000)
                } else {
                    throw e
                }
            }
        }
    }
}