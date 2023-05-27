package kurenai.imsyncbot.telegram

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kurenai.imsyncbot.*
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.command.CommandDispatcher
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.qq.QQBot
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.chatInfoString
import moe.kurenai.tdlight.AbstractUpdateSubscriber
import moe.kurenai.tdlight.LongPollingCoroutineTelegramBot
import moe.kurenai.tdlight.client.TDLightCoroutineClient
import moe.kurenai.tdlight.exception.TelegramApiRequestException
import moe.kurenai.tdlight.model.ResponseWrapper
import moe.kurenai.tdlight.model.command.BotCommand
import moe.kurenai.tdlight.model.command.BotCommandScope
import moe.kurenai.tdlight.model.command.BotCommandScopeType
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardButton
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardMarkup
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.Request
import moe.kurenai.tdlight.request.command.DeleteMyCommands
import moe.kurenai.tdlight.request.command.GetMyCommands
import moe.kurenai.tdlight.request.command.SetMyCommands
import moe.kurenai.tdlight.request.message.*
import moe.kurenai.tdlight.util.DefaultMapper.MAPPER
import moe.kurenai.tdlight.util.getLogger
import java.nio.file.Files
import java.time.LocalTime
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramBot(
    private val parentCoroutineContext: CoroutineContext,
    internal val telegramProperties: TelegramProperties,
    internal val bot: ImSyncBot,
) : AbstractUpdateSubscriber() {

    companion object {
        internal val log = getLogger()
    }

    //发送消息
    internal val messageOutChannel = Channel<ChannelMessage<*>>()

    //接收消息
    private val updateMessageChannel = Channel<Update>()

    private val coroutineContext = parentCoroutineContext + CoroutineName("TelegramBot")

    @OptIn(DelicateCoroutinesApi::class)
    private val workerContext = newFixedThreadPoolContext(10, "${telegramProperties.username}-worker")
    private val handlerContext = coroutineContext + workerContext.limitedParallelism(9)

    private val updateContext =
        coroutineContext + workerContext.limitedParallelism(1) + CoroutineName("TelegramHandleUpdate")
    private val sendMessageContext =
        coroutineContext + workerContext.limitedParallelism(1) + CoroutineName("TelegramSendMessage")

    val statusChannel = Channel<TelegramBotStatus>(Channel.BUFFERED)
    val username: String = telegramProperties.username
    val token: String = telegramProperties.token
    lateinit var client: TDLightCoroutineClient

    val disposableHandlers = LinkedList<TelegramDisposableHandler>()

    internal lateinit var tgBot: LongPollingCoroutineTelegramBot
    suspend fun start() {
        log.debug("Telegram base url: ${telegramProperties.baseUrl}")
        client = TDLightCoroutineClient(
            telegramProperties.baseUrl,
            telegramProperties.token,
            isUserMode = false,
            isDebugEnabled = true,
            updateBaseUrl = telegramProperties.baseUrl
        )
        statusChannel.send(Initialized)
        updateCommand()
        CoroutineScope(coroutineContext).launch {
            tgBot = LongPollingCoroutineTelegramBot(listOf(this@TelegramBot), client).apply {
                coroutineContext = this@TelegramBot.coroutineContext
            }
            tgBot.start()
            SendMessage(bot.configProperties.bot.masterOfTg.toString(), "启动成功")
        }
        CoroutineScope(coroutineContext).launch {
            sendMessage()
        }
        CoroutineScope(coroutineContext).launch {
            handleUpdate()
        }
    }

    private suspend fun updateCommand() = runCatching {
        log.info("Updating command...")
        client.send(DeleteMyCommands())
        client.send(DeleteMyCommands(BotCommandScope(BotCommandScopeType.ALL_GROUP_CHATS)))
        client.send(DeleteMyCommands(BotCommandScope(BotCommandScopeType.ALL_PRIVATE_CHATS)))
        if (!client.send(
                SetMyCommands(
                    tgCommands.filter { !it.onlyUserMessage }.map { BotCommand(it.command.lowercase(), it.help) },
                    BotCommandScope(BotCommandScopeType.ALL_GROUP_CHATS)
                )
            )
        ) {
            log.warn("Update all group chats command fail!")
        }
        if (!client.send(
                SetMyCommands(
                    tgCommands.filter { !it.onlyGroupMessage }.map { BotCommand(it.command.lowercase(), it.help) },
                    BotCommandScope(BotCommandScopeType.ALL_PRIVATE_CHATS)
                )
            )
        ) {
            log.warn("Update all private chats command fail!")
        }
        log.info("Update command finish.")
        if (log.isDebugEnabled) {
            client.send(GetMyCommands(scope = BotCommandScope(BotCommandScopeType.ALL_GROUP_CHATS))).forEach {
                log.debug("{}({}) - {}", it.command, BotCommandScopeType.ALL_GROUP_CHATS, it.description)
            }
            client.send(GetMyCommands(scope = BotCommandScope(BotCommandScopeType.ALL_PRIVATE_CHATS))).forEach {
                log.debug("{}({}) - {}", it.command, BotCommandScopeType.ALL_PRIVATE_CHATS, it.description)
            }
        }
    }.onFailure {
        log.error(it.message, it)
    }

    @Suppress("UNCHECKED_CAST")
    private tailrec suspend fun sendMessage() {
        val message = messageOutChannel.receive() as ChannelMessage<Any>
        CoroutineScope(sendMessageContext).launch {
            var ex: Throwable? = null
            var count = 0
            var response: Any? = null
            try {
                while (count <= 0) {
                    val request = message.request
                    try {
                        response = client.send(request, baseUrl = message.baseUrl)
                        count++
                    } catch (e: Exception) {
                        ex?.also { it.addSuppressed(e) } ?: kotlin.run { ex = e }
                        when (e) {
                            is TelegramApiRequestException -> {
                                val retryAfter = e.response?.parameters?.retryAfter
                                if (retryAfter != null) {
                                    log.info("Wait for ${retryAfter}s")
                                    delay(retryAfter * 1000L)
                                } else if (e.response?.description?.contains("wrong file identifier/HTTP URL specified") == true) {
                                    val inputFile = (request as? SendDocument)?.let {
                                        request.document
                                    } ?: (request as? SendPhoto)?.let {
                                        request.photo
                                    } ?: (request as? SendAnimation)?.let {
                                        request.animation
                                    } ?: (request as? SendAudio)?.let {
                                        request.audio
                                    }
                                    inputFile?.let { file ->
                                        log.warn(e.response?.description + ": ${inputFile.attachName}")
                                        val filename = file.fileName ?: UUID.randomUUID().toString()
                                        BotUtil.downloadImg(file.fileName ?: UUID.randomUUID().toString(), file.attachName).let {
                                            file.file = it.toFile()
                                            file.attachName = "attach://${filename}"
                                            file.mimeType = Files.probeContentType(it)
                                        }
                                        response = client.send(request)
                                    } ?: kotlin.run {
                                        log.warn("消息发送失败", e)
                                    }
                                    count++
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
            } catch (e: Exception) {
                log.error("Send message error", e)
            } finally {
                message.result.complete(response?.let {
                    Result.success(it)
                } ?: Result.failure(ex ?: BotException("异常不应该为空")))
            }
        }
        sendMessage()
    }

    private tailrec suspend fun handleUpdate() {
        try {
            val update = updateMessageChannel.receive()
            CoroutineScope(updateContext).launch {
                try {
                    disposableHandlers.firstOrNull {
                        if (it.timeout.plusMinutes(20).isBefore(LocalTime.now())) {
                            disposableHandlers.remove(it)
                            false
                        } else {
                            it.handle(bot, update)
                        }
                    }?.also {
                        disposableHandlers.remove(it)
                    } ?: run {
                        doHandleUpdate(update)
                    }
                } catch (e: Exception) {
                    reportError(
                        update,
                        BotException("Error on update received: ${e.message ?: e::class.java.simpleName}", e)
                    )
                }
            }
        } catch (e: Exception) {
            parentCoroutineContext.cancel(CancellationException("Handle update error", e))
            throw e
        }
        handleUpdate()
    }

    override fun onComplete0() {}

    override fun onError0(e: Throwable) {}

    override fun onNext0(update: Update) {
        runBlocking {
            updateMessageChannel.send(update)
        }
    }

    override fun onSubscribe0() {}

    suspend fun doHandleUpdate(update: Update) {
        log.debug("${update.chatInfoString()}: {}", MAPPER.writeValueAsString(update))
        val qqStatus = bot.qq.status.value
        if (qqStatus != QQBot.Initialized || qqStatus != QQBot.Online) {
            log.warn("QQ bot is not initialized or online, don't handle update.")
            return
        }
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
        } else if (bot.userConfig.chatIdFriends.containsKey(message.chat.id)) {
            bot.tgMessageHandler.onFriendMessage(message)
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
        val result: CompletableDeferred<Result<T>>,
        val baseUrl: String? = null
    )
}


suspend fun <T> Request<ResponseWrapper<T>>.sendCatching(bot: TelegramBot? = null, baseUrl: String? = null): Result<T> {
    val tg = bot ?: getBotOrThrow().tg
    val result = CompletableDeferred<Result<T>>()
    tg.messageOutChannel.send(TelegramBot.ChannelMessage(this, result, baseUrl = baseUrl))
    return withTimeout(30.seconds) { result.await() }
}

suspend fun <T> Request<ResponseWrapper<T>>.send(bot: TelegramBot? = null, baseUrl: String? = null): T {
    return this.sendCatching(bot, baseUrl).getOrThrow()
}