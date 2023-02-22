package kurenai.imsyncbot.telegram

import com.elbekd.bot.Bot
import com.elbekd.bot.model.toChatId
import com.elbekd.bot.util.SendingByteArray
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kurenai.imsyncbot.*
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.command.CommandDispatcher
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.qq.QQBotClient
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.chatInfoString
import moe.kurenai.tdlight.AbstractUpdateSubscriber
import moe.kurenai.tdlight.LongPollingCoroutineTelegramBot
import moe.kurenai.tdlight.client.TDLightCoroutineClient
import moe.kurenai.tdlight.exception.TelegramApiRequestException
import moe.kurenai.tdlight.model.ResponseWrapper
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardButton
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardMarkup
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.Request
import moe.kurenai.tdlight.request.message.*
import moe.kurenai.tdlight.util.DefaultMapper.MAPPER
import moe.kurenai.tdlight.util.getLogger
import kotlin.coroutines.CoroutineContext

/**
 * 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramBot(
    parentCoroutineContext: CoroutineContext,
    private val telegramProperties: TelegramProperties,
    private val bot: ImSyncBot,
) : AbstractUpdateSubscriber() {

    companion object {
        internal val log = getLogger()
    }

    //发送消息
    internal val messageOutChannel = Channel<ChannelMessage<*>>()

    //接收消息
    private val messageInChannel = Channel<Update>()

    private val coroutineContext = parentCoroutineContext + CoroutineName("TelegramBot") + SupervisorJob(parentCoroutineContext[Job])

    @OptIn(DelicateCoroutinesApi::class)
    private val workerContext = newFixedThreadPoolContext(10, "${telegramProperties.username}-worker")
    private val handlerContext = parentCoroutineContext + workerContext.limitedParallelism(9)
    val statusChannel = Channel<TelegramBotStatus>(Channel.BUFFERED)
    val username: String = telegramProperties.username
    val token: String = telegramProperties.token
    lateinit var client: TDLightCoroutineClient
    private val httpClient = HttpClient()
    private val _bot = Bot.createPolling(telegramProperties.username, telegramProperties.token)

    internal lateinit var tgBot: LongPollingCoroutineTelegramBot
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
                coroutineContext = this@TelegramBot.coroutineContext
            }
            tgBot.start()
        }
        CoroutineScope(coroutineContext + workerContext.limitedParallelism(1) + CoroutineName("TelegramSendMessage")).launch {
            sendMessage()
        }
        CoroutineScope(coroutineContext + workerContext.limitedParallelism(1) + CoroutineName("TelegramHandleUpdate")).launch {
            handleUpdate()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private tailrec suspend fun sendMessage() {
        try {
            val message = messageOutChannel.receive() as ChannelMessage<Any>

            var ex: Throwable? = null
            var count = 0
            var response: Any? = null

            while (count <= 0) {
                val request = message.request
                try {
                    response = client.send(request)
                } catch (e: Exception) {
                    ex?.also { it.addSuppressed(e) } ?: kotlin.run { ex = e }
                    when (e) {
                        is TelegramApiRequestException -> {
                            val retryAfter = e.response?.parameters?.retryAfter
                            if (retryAfter != null) {
                                log.info("Wait for ${retryAfter}s")
                                delay(retryAfter * 1000L)
                            } else if (e.response?.description?.contains("wrong file identifier/HTTP URL specified") == true) {
                                (request as? SendDocument)?.let {
                                    val bytes = httpClient.get(request.document.attachName).body<ByteArray>()
                                    _bot.sendDocument(
                                        request.chatId.toChatId(),
                                        SendingByteArray(bytes, request.document.fileName),
                                        caption = request.caption,
                                        replyToMessageId = request.replyToMessageId?.toLong()
                                    )
                                }
                                (request as? SendPhoto)?.let {
                                    val bytes = httpClient.get(request.photo.attachName).body<ByteArray>()
                                    _bot.sendPhoto(
                                        request.chatId.toChatId(),
                                        SendingByteArray(bytes, request.photo.fileName),
                                        caption = request.caption,
                                        replyToMessageId = request.replyToMessageId?.toLong()
                                    )
                                }
                                (request as? SendAnimation)?.let {
                                    val bytes = httpClient.get(request.animation.attachName).body<ByteArray>()
                                    _bot.sendAnimation(
                                        request.chatId.toChatId(),
                                        SendingByteArray(bytes, request.animation.fileName),
                                        caption = request.caption,
                                        replyToMessageId = request.replyToMessageId?.toLong()
                                    )
                                }
                                (request as? SendAudio)?.let {
                                    val bytes = httpClient.get(request.audio.attachName).body<ByteArray>()
                                    _bot.sendAudio(
                                        request.chatId.toChatId(),
                                        SendingByteArray(bytes, request.audio.fileName),
                                        caption = request.caption,
                                        replyToMessageId = request.replyToMessageId?.toLong()
                                    )
                                }
                            } else {
                                count++
                                log.warn("消息发送失败", e)
                            }
                        }

                        else -> {
                            count++
                            log.warn("消息发送失败", e)
                        }
                    }
                }
            }
            message.result.complete(response?.let {
                Result.success(it)
            } ?: Result.failure(ex ?: BotException("异常不应该为空")))
        } catch (e: Exception) {
            log.error("Send message error", e)
        }
        sendMessage()
    }

    private tailrec suspend fun handleUpdate() {
        try {
            val update = messageInChannel.receive()
            try {
                onUpdateReceivedSuspend(update)
            } catch (e: Exception) {
                reportError(
                    update,
                    BotException("Error on update received: ${e.message ?: e::class.java.simpleName}", e)
                )
            }
        } catch (e: Exception) {
            log.error("Handle update error", e)
        }
        handleUpdate()
    }

    override fun onComplete0() {
    }

    override fun onError0(e: Throwable) {
    }

    //    override fun onNext0(update: Update) {
//        CoroutineScope(handlerContext).launch {
//            messageReceiveLock.withLock {
//                try {
//                    onUpdateReceivedSuspend(update)
//                } catch (e: Exception) {
//                    reportError(
//                        update,
//                        BotException("Error on update received: ${e.message ?: e::class.java.simpleName}", e)
//                    )
//                }
//            }
//        }
//    }
    override fun onNext0(update: Update) {
        CoroutineScope(handlerContext).launch {
            messageInChannel.send(update)
        }
    }

    override fun onSubscribe0() {
    }

    suspend fun onUpdateReceivedSuspend(update: Update) {
        log.info("${update.chatInfoString()}: {}", MAPPER.writeValueAsString(update))
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
                launch(handlerContext) {
                    CommandDispatcher.execute(update, message)
                }
            }
        } else if (update.hasInlineQuery()) {
            coroutineScope {
                launch(handlerContext) {
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
            log.error("${update.chatInfoString()} Report error task fail: ${e.message}", e)
        }
    }

    sealed interface TelegramBotStatus
    object Initializing : TelegramBotStatus
    object Initialized : TelegramBotStatus

    data class ChannelMessage<T>(
        val request: Request<ResponseWrapper<T>>,
        val result: CompletableDeferred<Result<T>>
    )
}

//suspend fun <T> Request<ResponseWrapper<T>>.sendCatching(param: TelegramBot? = null): Result<T> {
//    return kotlin.runCatching {
//        send(param)
//    }
//}

//suspend fun <T> Request<ResponseWrapper<T>>.send(param: TelegramBot? = null): T {
//    val bot = param ?: getBotOrThrow().tg
//    var ex: Throwable? = null
//    bot.clientLock.withLock {
//        var count = 0
//        while (count <= 0) {
//            try {
//                return bot.client.send(this@send)
//            } catch (e: Exception) {
//                ex?.also { it.addSuppressed(e) } ?: kotlin.run { ex = e }
//                when (e) {
//                    is TelegramApiRequestException -> {
//                        e.response?.parameters?.retryAfter?.also { retryAfter ->
//                            log.info("Wait for ${retryAfter}s")
//                            delay(retryAfter * 1000L)
//                        } ?: run {
//                            count++
//                            log.warn("消息发送失败", e)
//                        }
//                    }
//                    else -> {
//                        count++
//                        log.warn("消息发送失败", e)
//                    }
//                }
//            }
//        }
//        throw BotException(ex!!.message?.replace(bot.token, "{mask}") ?: "信息发送失败", ex)
//    }
//}

suspend fun <T> Request<ResponseWrapper<T>>.sendCatching(bot: TelegramBot? = null): Result<T> {
    val tg = bot ?: getBotOrThrow().tg
    val result = CompletableDeferred<Result<T>>()
    tg.messageOutChannel.send(TelegramBot.ChannelMessage(this, result))
    return result.await()
}

suspend fun <T> Request<ResponseWrapper<T>>.send(bot: TelegramBot? = null): T {
    return this.sendCatching(bot).getOrThrow()
}