package kurenai.imsyncbot.telegram

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.*
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.command.CommandDispatcher
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.qq.QQBotClient
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.childScopeContext
import moe.kurenai.tdlight.AbstractUpdateSubscriber
import moe.kurenai.tdlight.LongPollingCoroutineTelegramBot
import moe.kurenai.tdlight.client.TDLightCoroutineClient
import moe.kurenai.tdlight.exception.TelegramApiRequestException
import moe.kurenai.tdlight.model.ResponseWrapper
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardButton
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardMarkup
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.Request
import moe.kurenai.tdlight.request.message.SendMessage
import moe.kurenai.tdlight.util.DefaultMapper.MAPPER
import org.apache.logging.log4j.LogManager
import kotlin.coroutines.CoroutineContext

/**
 * 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */
class TelegramBot(
    parentCoroutineContext: CoroutineContext,
    private val telegramProperties: TelegramProperties,
    private val bot: ImSyncBot,
) : AbstractUpdateSubscriber(), CoroutineScope {

    companion object {
        internal val log = LogManager.getLogger()
    }

    internal val clientLock = Mutex()
    private val messageReceiveLock = Mutex()
    var statusChannel = Channel<TelegramBotStatus>(Channel.BUFFERED)
    val username: String = telegramProperties.username
    val token: String = telegramProperties.token
    lateinit var client: TDLightCoroutineClient

    internal lateinit var tgBot: LongPollingCoroutineTelegramBot
    override val coroutineContext: CoroutineContext = CoroutineName("TelegramBot.$").plus(
        CoroutineExceptionHandler { context, e ->
            log.error(context[CoroutineName]?.let { "Exception in coroutine '${it.name}'." }
                ?: "Exception in unnamed coroutine.", e)
        }).childScopeContext(parentCoroutineContext)
        .apply {
            job.invokeOnCompletion {
                kotlin.runCatching {
                    client.close()
                }.onFailure {
                    if (it !is CancellationException) log.error(it)
                }
            }
        }

    suspend fun start() {
        CoroutineScope(coroutineContext).launch {
            log.debug("Telegram base url: ${telegramProperties.baseUrl}")
            client = TDLightCoroutineClient(
                telegramProperties.baseUrl,
                telegramProperties.token,
                isUserMode = false,
                isDebugEnabled = true,
                updateBaseUrl = telegramProperties.baseUrl
            )
            statusChannel.send(Initialized)
            var qqBotStatus = bot.qq.statusChannel.receive()
            while (qqBotStatus !is QQBotClient.Initialized) {
                log.debug("QQ bot status: ${qqBotStatus.javaClass.simpleName}")
                qqBotStatus = bot.qq.statusChannel.receive()
            }
            tgBot = LongPollingCoroutineTelegramBot(listOf(this@TelegramBot), client).apply {
                coroutineContext = coroutineContext.childScopeContext(this@TelegramBot.coroutineContext)
            }
            tgBot.start()
        }
    }

    override fun onComplete0() {
    }

    override fun onError0(e: Throwable) {
    }

    override fun onNext0(update: Update) {
        CoroutineScope(coroutineContext).launch {
            messageReceiveLock.withLock {
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
    }

    override fun onSubscribe0() {
    }

    suspend fun onUpdateReceivedSuspend(update: Update) {
        log.info("onUpdateReceived: {}", MAPPER.writeValueAsString(update))
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
                reportError(update, e, "执行回调失败")
            }
        }

        if (message.isCommand()) {
            coroutineScope {
                launch(this@TelegramBot.coroutineContext) {
                    CommandDispatcher.execute(update, message)
                }
            }
        } else if (update.hasInlineQuery()) {
            coroutineScope {
                launch(this@TelegramBot.coroutineContext) {
                    CommandDispatcher.handleInlineQuery(update, update.inlineQuery!!)
                }
            }
        } else if (message.chat.id == bot.privateHandle.privateChat) {
            bot.privateHandle.onPrivateChat(update)
        } else if ((message.isGroupMessage() || message.isSuperGroupMessage())) {
            var handled = false
            if (update.hasMessage()) {
                for (handler in tgHandlers) {
                    if (handler.onMessage(message) == END) {
                        handled = true
                        break
                    }
                }
                if (!handled) bot.tgMessageHandler.onMessage(message)
            } else if (update.hasEditedMessage()) {
                for (handler in tgHandlers) {
                    if (handler.onEditMessage(update.editedMessage!!) == END) {
                        handled = true
                        break
                    }
                }
                if (!handled) bot.tgMessageHandler.onEditMessage(update.editedMessage!!)
            }
        }
    }

    suspend fun reportError(update: Update, throwable: Throwable, topic: String = "转发失败") {
        log.error(throwable.message ?: throwable::class.java.simpleName, throwable)
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

            val errorMsg = "#$topic\n${throwable::class.simpleName}: ${throwable.message?.replace(telegramProperties.token, "{token}")}"
            SendMessage(message.chatId, errorMsg).apply {
                this.replyToMessageId = message.messageId
                this.replyMarkup =
                    InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("重试").apply {
                        this.callbackData = "retry"
                    })))
            }.send(this)
            CacheService.cache(message)
        } catch (e: Exception) {
            log.error("Report error task fail: ${e.message}", e)
        }
    }

    sealed interface TelegramBotStatus
    object Initializing : TelegramBotStatus
    object Initialized : TelegramBotStatus
}

suspend fun <T> Request<ResponseWrapper<T>>.send(param: TelegramBot? = null): T {
    val bot = param ?: getBotOrThrow().tg
    var lastEx: Throwable? = null
    bot.clientLock.withLock {
        var count = 0
        while (count <= 0) {
            try {
                return bot.client.send(this@send)
            } catch (e: Exception) {
                lastEx = e
                when (e) {
                    is TelegramApiRequestException -> {
                        e.response?.parameters?.retryAfter?.also { retryAfter ->
                            log.info("Wait for ${retryAfter}s")
                            delay(retryAfter * 1000L)
                        } ?: run {
                            log.warn("消息发送失败", e)
                        }
                    }

                    else -> {
                        log.warn("消息发送失败", e)
                    }
                }
                count++
            }
        }
    }
    throw lastEx?.let {
        BotException(it.message?.replace(bot.token, "{mask}") ?: "信息发送失败")
    } ?: BotException("信息发送失败")
}