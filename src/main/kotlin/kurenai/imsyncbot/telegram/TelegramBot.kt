package kurenai.imsyncbot.telegram

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.callbacks
import kurenai.imsyncbot.command.DelegatingCommand
import kurenai.imsyncbot.configProperties
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.qq.QQBotClient
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBot.client
import kurenai.imsyncbot.tgHandlers
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
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */
object TelegramBot : AbstractUpdateSubscriber() {

    private val telegramProperties = configProperties.bot.telegram

    val username: String = telegramProperties.username
    val token: String = telegramProperties.token
    lateinit var client: TDLightClient

    private val log = LogManager.getLogger()
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

    suspend fun start() {
//        GetChatMember(GroupConfig.tgQQ[0])

        log.debug("Telegram base url: ${telegramProperties.baseUrl}")
        client = TDLightClient(
            telegramProperties.baseUrl,
            telegramProperties.token,
            isUserMode = false,
            isDebugEnabled = true,
            updateBaseUrl = telegramProperties.baseUrl
        )
        withContext(Dispatchers.IO) {
            QQBotClient.startCountDown.await()
        }
        bot = LongPollingTelegramBot(listOf(this), client)
        log.info("Started telegram-bot $username")
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
                reportError(
                    update,
                    BotException("Error on update received: ${e.message ?: e::class.java.simpleName}", e)
                )
            }
        }
    }

    override fun onSubscribe0() {
    }

    suspend fun onUpdateReceivedSuspend(update: Update) {
        log.debug("onUpdateReceived: {}", MAPPER.writeValueAsString(update))
        val message = update.message ?: update.editedMessage ?: update.callbackQuery?.message
        if (message == null) {
            log.debug("No message")
            return
        }

        if (update.hasCallbackQuery()) {
            try {
                for (callback in callbacks) {
                    if (callback.match(update) && callback.handle(update, message) == Callback.END) {
                        return
                    }
                }
            } catch (e: Exception) {
                log.error(e.message, e)
                reportError(update, e, "执行回调失败", false)
            }
        }

        if (message.isCommand()) {
            DelegatingCommand.execute(update, message)
        } else if (update.hasInlineQuery()) {
            DelegatingCommand.handleInlineQuery(update, update.inlineQuery!!)
        } else if (message.chat.id == PrivateChatHandler.privateChat) {
            PrivateChatHandler.onPrivateChat(update)
        } else if ((message.isGroupMessage() || message.isSuperGroupMessage())) {
            if (update.hasMessage()) {
                for (handler in tgHandlers) {
                    if (handler.onMessage(message) == END) break
                }
            } else if (update.hasEditedMessage()) {
                for (handler in tgHandlers) {
                    if (handler.onEditMessage(update.editedMessage!!) == END) break
                }
            }
        }
    }

    suspend fun reportError(update: Update, e: Throwable, topic: String = "转发失败", canRetry: Boolean = true) {
        log.error(e.message ?: e::class.java.simpleName, e)
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
                    InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("重试").apply {
                        this.callbackData = "retry"
                    })))
            }.send()
            CacheService.cache(message)
        } catch (e: Exception) {
            log.error(e.message, e)
        }
    }
}

val log: Logger = LogManager.getLogger()
val clientLock = Mutex()

suspend fun <T> Request<ResponseWrapper<T>>.send(): T {
    var count = 0
    var lastEx: Throwable? = null
    while (count < 3) {
        clientLock.withLock {
            kotlin.runCatching {
                client.sendSync(this@send)
            }.onFailure {
                lastEx = it
                if (it is TelegramApiRequestException) {
                    it.response?.parameters?.retryAfter?.let { retryAfter ->
                        log.info("Wait for ${retryAfter}s")
                        delay(retryAfter * 1000L)
                    }
                } else {
                    throw it
                }
                log.error(it.message, it)
                count++
            }.onSuccess {
                return it
            }
        }
    }
    log.warn("重试3次失败")
    throw lastEx?.let {
        BotException(it.message ?: "重试3次失败", it)
    } ?: BotException("重试3次失败")
}