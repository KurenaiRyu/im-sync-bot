package kurenai.imsyncbot.bot.telegram

import com.sksamuel.aedile.core.caffeineBuilder
import it.tdlight.client.*
import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kurenai.imsyncbot.*
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.ParseMode
import kurenai.imsyncbot.utils.TelegramUtil.asFmtText
import kurenai.imsyncbot.utils.TelegramUtil.fmt
import kurenai.imsyncbot.utils.TelegramUtil.messageText
import kurenai.imsyncbot.utils.TelegramUtil.userSender
import kurenai.imsyncbot.utils.getLogger
import kurenai.imsyncbot.utils.withIO
import okhttp3.internal.toHexString
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.Result
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */

lateinit var defaultTelegramBot: TelegramBot

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramBot(
    parentCoroutineContext: CoroutineContext,
    internal val telegramProperties: TelegramProperties,
    internal val bot: ImSyncBot,
) {

    companion object {
        val log = getLogger()
    }

    private lateinit var apiToken: APIToken

    // Configure the client
    private lateinit var settings: TDLibSettings

    lateinit var client: SimpleTelegramClient

    val messageHandler: TgMessageHandler = TgMessageHandler(bot)

    //发送消息
    internal val messageOutChannel = Channel<ChannelMessage<*>>()

    //接收消息
    private val updateMessageChannel = Channel<Update>()

    val coroutineContext = parentCoroutineContext + CoroutineName("TelegramBot")

    @OptIn(DelicateCoroutinesApi::class)
    private val workerContext = newFixedThreadPoolContext(10, "${telegramProperties.username}-worker")
    private val handlerContext = coroutineContext + workerContext.limitedParallelism(9)

    val updateContext =
        coroutineContext + workerContext.limitedParallelism(1) + CoroutineName("TelegramHandleUpdate")
    val sendMessageContext =
        coroutineContext + workerContext.limitedParallelism(1) + CoroutineName("TelegramSendMessage")

    val status = MutableStateFlow<BotStatus>(Initializing)
    val token: String = telegramProperties.token

    val disposableHandlers = LinkedList<TelegramDisposableHandler>()

    val editedMessages = caffeineBuilder<String, Boolean> {
        maximumSize = 200
        expireAfterWrite = 5.minutes
    }.build()

    val pendingMessage = caffeineBuilder<Long, CancellableContinuation<it.tdlight.client.Result<Object>>> {
        maximumSize = 200
        expireAfterWrite = 5.minutes
    }.build()

    suspend fun start() {
        apiToken = APIToken(
            telegramProperties.apiId ?: 94575,
            telegramProperties.apiHash ?: "a3406de8d171bb422bb6ddf3bbd800e2"
        )
        settings = TDLibSettings.create(apiToken).apply {
            // Configure the session directory
            val sessionPath = Paths.get("tdlib").resolve(token.substringBefore(":"))
            databaseDirectoryPath = sessionPath.resolve("data")
            downloadedFilesDirectoryPath = sessionPath.resolve("downloads")
            isFileDatabaseEnabled = true
            isChatInfoDatabaseEnabled = true
            isMessageDatabaseEnabled = true
        }
        client =
            SimpleTelegramClientFactory().builder(settings).build(AuthenticationSupplier.bot(telegramProperties.token))
        client.addUpdateHandler(UpdateAuthorizationState::class.java) { update ->
            if (update.authorizationState.constructor == AuthorizationStateReady.CONSTRUCTOR) {
                log.info("Telegram bot started.")
                status.update { Running }
                if (!::defaultTelegramBot.isInitialized) defaultTelegramBot = this@TelegramBot
                client.addUpdatesHandler(messageHandler::handle)
                CoroutineScope(coroutineContext).launch {
                    updateCommand()
                }
            }
        }
    }

    suspend inline fun sendMessageText(
        text: String,
        chatId: Long,
        parseMode: ParseMode = ParseMode.TEXT,
        replayToMessageId: Long? = null,
        messageThreadId: Long? = null,
        untilPersistent: Boolean = false,
    ) = sendMessageText(text.fmt(parseMode), chatId, replayToMessageId, messageThreadId, untilPersistent)

    suspend inline fun sendMessageText(
        formattedText: FormattedText,
        chatId: Long,
        replayToMessageId: Long? = null,
        messageThreadId: Long? = null,
        untilPersistent: Boolean = false,
    ) = send(untilPersistent) {
        messageText(formattedText, chatId).apply {
            replayToMessageId?.let { this.replyToMessageId = it }
            messageThreadId?.let { this.messageThreadId = it }
        }
    }

    suspend inline fun sendMessagePhoto(
        url: String,
        text: String,
        chatId: Long,
        parseMode: ParseMode = ParseMode.TEXT,
        filename: String = System.currentTimeMillis().toHexString(),
        replayToMessageId: Long? = null,
        messageThreadId: Long? = null,
        untilPersistent: Boolean = false,
    ) = sendMessagePhoto(
        url,
        text.fmt(parseMode),
        chatId,
        filename,
        replayToMessageId,
        messageThreadId,
        untilPersistent
    )

    suspend fun sendMessagePhoto(
        url: String,
        formattedText: FormattedText,
        chatId: Long,
        filename: String = System.currentTimeMillis().toHexString(),
        replayToMessageId: Long? = null,
        messageThreadId: Long? = null,
        untilPersistent: Boolean = false,
    ) = send(untilPersistent = untilPersistent) {
        SendMessage().apply {
            replayToMessageId?.let { this.replyToMessageId = it }
            messageThreadId?.let { this.messageThreadId = it }
            this.inputMessageContent = InputMessagePhoto().apply {
                this.photo = InputFileLocal(BotUtil.downloadImg(filename, url).pathString)
                this.caption = formattedText
            }
        }
        messageText(formattedText, chatId).apply {
            replayToMessageId?.let { this.replyToMessageId = it }
            messageThreadId?.let { this.messageThreadId = it }
        }
    }

    suspend fun sendMessagePhoto(
        data: ByteArray,
        formattedText: FormattedText,
        chatId: Long,
        filename: String = "${System.currentTimeMillis()}",
        replayToMessageId: Long? = null,
        untilPersistent: Boolean = false,
    ) = send(untilPersistent = untilPersistent) {
        val path = Path.of(BotUtil.getImagePath(filename))
        path.writeBytes(data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        SendMessage().apply {
            this.chatId = chatId
            replayToMessageId?.let { this.replyToMessageId = replayToMessageId }
            this.inputMessageContent = InputMessagePhoto().apply {
                this.caption = formattedText
                this.photo = InputFileLocal(path.pathString)
            }
        }
    }

    suspend fun deleteMessages(chatId: Long, vararg messageIds: Long) {
        require(messageIds.isNotEmpty()) { "message id cannot be null" }
        send {
            DeleteMessages().apply {
                this.chatId = chatId
                this.messageIds = messageIds
            }
        }
    }

    suspend inline fun downloadFile(
        file: File,
        limit: Long = 0,
        offset: Long = 0,
        priority: Int = 10,
        synchronous: Boolean = true
    ) = if (file.local.isDownloadingCompleted) {
        file
    } else {
        downloadFile(file.id, limit, offset, priority, synchronous)
    }

    suspend inline fun downloadFile(
        fileId: Int,
        limit: Long = 0,
        offset: Long = 0,
        priority: Int = 10,
        synchronous: Boolean = true
    ): File {
        val downloadFile = DownloadFile().apply {
            this.fileId = fileId
            this.limit = limit
            this.offset = offset
            this.priority = priority
            this.synchronous = synchronous
        }
        return if (synchronous) withIO { send(downloadFile) }
        else send(downloadFile)
    }

    suspend inline fun getMessage(chatId: Long, messageId: Long) = send {
        GetMessage(chatId, messageId)
    }

    suspend inline fun getChatMember(chatId: Long, sender: MessageSender) = send {
        GetChatMember(chatId, sender)
    }

    suspend inline fun getUser(userId: Long) = send {
        GetUser(userId)
    }

    suspend inline fun getUser(message: Message) = message.userSender()?.let { user ->
        send {
            GetUser(user.userId)
        }
    }

    fun getMe(): User = client.me

    suspend inline fun getChat(chatId: Long) = send {
        GetChat(chatId)
    }

    fun getUsername(): String = client.me.usernames.activeUsernames.first()

    suspend inline fun <R : Object, Fun : TdApi.Function<R>> send(
        func: Fun,
        untilPersistent: Boolean = false,
        timeout: Duration = 5.seconds
    ): R = send(untilPersistent, timeout) { func }

    @Suppress("UNCHECKED_CAST")
    suspend fun <R : Object> send(
        untilPersistent: Boolean = false,
        timeout: Duration = 5.seconds,
        block: suspend () -> TdApi.Function<R>
    ): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        val result = suspendCancellableCoroutine<it.tdlight.client.Result<R>> { con ->
            CoroutineScope(coroutineContext).launch {
                client.send(block.invoke()) { result ->
                    if (untilPersistent && !result.isError) {
                        val obj = result.get()
                        if ((obj as? Message)?.sendingState?.constructor == MessageSendingStatePending.CONSTRUCTOR) {
                            pendingMessage[obj.id] = con as CancellableContinuation<it.tdlight.client.Result<Object>>
                        } else {
                            con.resumeWith(Result.success(result))
                        }
                    } else {
                        con.resumeWith(Result.success(result))
                    }
                }
                delay(timeout)
                if (con.isActive) con.resumeWith(Result.failure(BotException("Telegram client timeout in $timeout s")))
            }
        }
        return if (result.isError && result.error.message.contains("retry after")) {
            val seconds = result.error.message.substringAfterLast(" ").toLongOrNull() ?: 200
            delay(seconds * 1000)
            send(untilPersistent, timeout, block)
        } else {
            result.get()
        }
    }

    private suspend fun updateCommand() = runCatching {
        send { DeleteCommands().apply { this.scope = BotCommandScopeAllPrivateChats() } }
        send { DeleteCommands().apply { this.scope = BotCommandScopeAllGroupChats() } }
        send {
            SetCommands().apply {
                this.scope = BotCommandScopeAllPrivateChats()
                this.commands = tgCommands.filter { it.onlyUserMessage }.map { cmd ->
                    BotCommand().apply {
                        this.command = cmd.name.lowercase()
                        this.description = cmd.help
                    }
                }.toTypedArray()
            }
        }

        send {
            SetCommands().apply {
                this.scope = BotCommandScopeAllGroupChats()
                this.commands = tgCommands.filter { it.onlyGroupMessage }.map { cmd ->
                    BotCommand().apply {
                        this.command = cmd.name.lowercase()
                        this.description = cmd.help
                    }
                }.toTypedArray()
            }
        }
    }.onFailure {
        log.error(it.message, it)
    }

//    @Suppress("UNCHECKED_CAST")
//    private tailrec suspend fun sendMessage() {
//        val message = messageOutChannel.receive() as ChannelMessage<Any>
//        CoroutineScope(sendMessageContext).launch {
//            var ex: Throwable? = null
//            var count = 0
//            var response: Any? = null
//            try {
//                while (count <= 0) {
//                    val request = message.request
//                    try {
//                        response = client.send(request, baseUrl = message.baseUrl)
//                        count++
//                    } catch (e: Exception) {
//                        ex?.also { it.addSuppressed(e) } ?: kotlin.run { ex = e }
//                        when (e) {
//                            is TelegramApiRequestException -> {
//                                val retryAfter = e.response?.parameters?.retryAfter
//                                if (retryAfter != null) {
//                                    log.info("Wait for ${retryAfter}s")
//                                    delay(retryAfter * 1000L)
//                                } else if (e.response?.description?.contains("wrong file identifier/HTTP URL specified") == true) {
//                                    val inputFile = (request as? SendDocument)?.let {
//                                        request.document
//                                    } ?: (request as? SendPhoto)?.let {
//                                        request.photo
//                                    } ?: (request as? SendAnimation)?.let {
//                                        request.animation
//                                    } ?: (request as? SendAudio)?.let {
//                                        request.audio
//                                    }
//                                    inputFile?.let { file ->
//                                        log.warn(e.response?.description + ": ${inputFile.attachName}")
//                                        val filename = file.fileName ?: UUID.randomUUID().toString()
//                                        BotUtil.downloadImg(
//                                            file.fileName ?: UUID.randomUUID().toString(),
//                                            file.attachName
//                                        ).let {
//                                            file.file = it.toFile()
//                                            file.attachName = "attach://${filename}"
//                                            file.mimeType = Files.probeContentType(it)
//                                        }
//                                        response = client.send(request)
//                                    } ?: kotlin.run {
//                                        log.warn("消息发送失败", e)
//                                    }
//                                    count++
//                                } else {
//                                    count++
//                                    log.warn("消息发送失败", e)
//                                }
//                            }
//
//                            else -> {
//                                count++
//                                log.warn("消息发送失败", e)
//                            }
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                log.error("Send message error", e)
//            } finally {
//                message.result.complete(response?.let {
//                    Result.success(it)
//                } ?: Result.failure(ex ?: BotException("异常不应该为空")))
//            }
//        }
//        sendMessage()
//    }

//    private tailrec suspend fun handleUpdate() {
//        try {
//            val update = updateMessageChannel.receive()
//            CoroutineScope(updateContext).launch {
//                try {
//                    disposableHandlers.firstOrNull {
//                        if (it.timeout.plusMinutes(20).isBefore(LocalTime.now())) {
//                            disposableHandlers.remove(it)
//                            false
//                        } else {
//                            it.handle(bot, update)
//                        }
//                    }?.also {
//                        disposableHandlers.remove(it)
//                    } ?: run {
//                        doHandleUpdate(update)
//                    }
//                } catch (e: Exception) {
//                    val ex = if (e is BotException) e else BotException(
//                        "处理消息失败: ${e.message ?: e::class.java.simpleName}",
//                        e
//                    )
//                    reportError(
//                        update,
//                        ex
//                    )
//                }
//            }
//        } catch (e: Exception) {
//            parentCoroutineContext.cancel(CancellationException("Handle update error", e))
//            throw e
//        }
//        handleUpdate()
//    }

//    suspend fun doHandleUpdate(update: Update) {
//        log.debug("${update.chatInfoString()}: {}", MAPPER.writeValueAsString(update))
//        val message = update.message ?: update.editedMessage ?: update.callbackQuery?.message
//        if (message == null) {
//            log.debug("No message")
//            return
//        }
//
//        if (update.hasCallbackQuery()) {
//            try {
//                for (callback in callbacks) {
//                    if (callback.match(update) && callback.handle(update, message) == Callback.END) {
//                        return
//                    }
//                }
//            } catch (e: Exception) {
//                reportError(update, e, "执行回调失败")
//            }
//        }
//
//        if (message.isCommand()) {
//            coroutineScope {
//                launch(handlerContext) {
//                    CommandDispatcher.execute(update, message)
//                }
//            }
////        } else if (update.hasInlineQuery()) {
////            coroutineScope {
////                launch(handlerContext) {
////                    CommandDispatcher.handleInlineQuery(update, update.inlineQuery!!)
////                }
////            }
//        } else if (checkQQBotStatus()) {
//            if (bot.userConfig.chatIdFriends.containsKey(message.chat.id)) {
//                bot.tgMessageHandler.onFriendMessage(message)
//            } else if ((message.isGroupMessage() || message.isSuperGroupMessage())) {
//                var handled = false
//                if (update.hasMessage()) {
//                    for (handler in tgHandlers) {
//                        if (handler.onMessage(message) == END) {
//                            handled = true
//                            break
//                        }
//                    }
//                    if (!handled) bot.tgMessageHandler.onMessage(message)
//                } else if (update.hasEditedMessage()) {
//                    for (handler in tgHandlers) {
//                        if (handler.onEditMessage(update.editedMessage!!) == END) {
//                            handled = true
//                            break
//                        }
//                    }
//                    if (!handled) bot.tgMessageHandler.onEditMessage(update.editedMessage!!)
//                }
//            }
//        }
//    }

    suspend fun sendError(message: Message, throwable: Throwable, topic: String = "转发失败") {
        try {
            val errorMsg = "#$topic\n${throwable::class.simpleName}: ${
                throwable.message?.replace(
                    telegramProperties.token,
                    "{token}"
                )
            }"

            send() {
                messageText(errorMsg.asFmtText(), message.chatId).apply {
                    this.replyToMessageId = message.id
                    this.options = MessageSendOptions().apply {
                        this.fromBackground = true
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Report error failed: {}", message.toString().trim(), e)
        }
    }

    data class ChannelMessage<T>(
        val request: Function<T>,
        val result: CompletableDeferred<Result<T>>
    )
}

//suspend inline fun <T> Function<T>.send(bot: TelegramBot? = null) = this.sendCatching(bot).getOrThrow()
//
//suspend inline fun <T> Function<T>.sendCatching(bot: TelegramBot? = null) = sendCatching(bot) { this }
//
//
//suspend inline fun <T> send(bot: TelegramBot? = null, noinline block: suspend () -> Function<T>) =
//    sendCatching(bot, block)
//
//suspend fun <T> sendCatching(bot: TelegramBot? = null, block: suspend () -> Function<T>): Result<T> {
//    val tg = bot ?: getBotOrThrow().tg
//    val result = CompletableDeferred<Result<T>>()
//    tg.messageOutChannel.send(TelegramBot.ChannelMessage(block(), result))
//    return withTimeout(30.seconds) { result.await() }
//}
