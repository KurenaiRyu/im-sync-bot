package kurenai.imsyncbot.bot.telegram

import com.sksamuel.aedile.core.caffeineBuilder
import it.tdlight.client.*
import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kurenai.imsyncbot.*
import kurenai.imsyncbot.utils.*
import okhttp3.internal.toHexString
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.Result
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue
import it.tdlight.client.Result as TdResult

/**
 * Telegram 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */

lateinit var defaultTelegramBot: TelegramBot

class TelegramBot(
    private val telegramProperties: TelegramProperties,
    internal val bot: ImSyncBot,
) : CoroutineScope {

    companion object {
        internal val log = getLogger()
        val DEFAULT_TIMEOUT = 10.seconds
    }

    private lateinit var apiToken: APIToken

    // Configure the client
    private lateinit var settings: TDLibSettings

    private lateinit var client: SimpleTelegramClient

    internal val messageHandler: TgMessageHandler = TgMessageHandler(bot)

    override val coroutineContext = bot.coroutineContext
        .plus(CoroutineName("TelegramBot"))
        .plus(SupervisorJob(bot.coroutineContext[Job]))
        .plus(CoroutineExceptionHandler { context, ex ->
            when (ex) {
                is CancellationException -> {
                    log.warn("{} was cancelled", context[CoroutineName])
                }

                else -> {
                    log.warn("with {}", context[CoroutineName], ex)
                }
            }
        })

    val status = MutableStateFlow<BotStatus>(Initializing)
    val token: String = telegramProperties.token

    val disposableHandlers = LinkedList<TelegramDisposableHandler>()

    val editedMessages = caffeineBuilder<String, Boolean> {
        maximumSize = 50
        expireAfterWrite = 1.minutes
    }.build()

    val pendingMessage = caffeineBuilder<Long, CancellableContinuation<Object>> {
        maximumSize = 50
        expireAfterWrite = 1.minutes
    }.build()

    fun start() {
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
                launch {
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
            replayToMessageId?.let { this.setReplyToMessageId(it) }
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
    ): Message {
        SendMessage().apply {
            replayToMessageId?.let { this.setReplyToMessageId(it) }
            messageThreadId?.let { this.messageThreadId = it }
            this.inputMessageContent = InputMessagePhoto().apply {
                this.photo = InputFileLocal(BotUtil.downloadImg(filename, url).pathString)
                this.caption = formattedText
            }
        }
        return send(untilPersistent = untilPersistent) {
            messageText(formattedText, chatId).apply {
                replayToMessageId?.let { this.setReplyToMessageId(it) }
                messageThreadId?.let { this.messageThreadId = it }
            }
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
            this.setReplyToMessageId(replayToMessageId)
            this.inputMessageContent = InputMessagePhoto().apply {
                this.caption = formattedText
                this.photo = InputFileLocal(path.pathString)
            }
        }
    }

    suspend fun sendMessageVideo(
        data: ByteArray,
        formattedText: FormattedText,
        chatId: Long,
        filename: String = "${System.currentTimeMillis()}",
        replayToMessageId: Long? = null,
        untilPersistent: Boolean = false,
    ) = send(untilPersistent = untilPersistent) {
        val path = Path.of(BotUtil.getDocumentPath(filename))
        path.writeBytes(data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        SendMessage().apply {
            this.chatId = chatId
            this.setReplyToMessageId(replayToMessageId)
            this.inputMessageContent = InputMessageVideo().apply {
                this.caption = formattedText
                this.video = InputFileLocal(path.pathString)
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
        function: Fun,
        untilPersistent: Boolean = false,
        timeout: Duration = DEFAULT_TIMEOUT
    ): R = send(untilPersistent, timeout) { function }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalTime::class)
    suspend fun <R : Object> send(
        untilPersistent: Boolean = false,
        timeout: Duration = DEFAULT_TIMEOUT,
        block: () -> TdApi.Function<R>
    ): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        val params = block()
        val (value, duration) = measureTimedValue {
            withContext(this.coroutineContext) {
                runCatching {
                    suspendCancellableCoroutine { con: CancellableContinuation<R> ->
                        var obj: R? = null
                        CoroutineScope(Dispatchers.IO).launch {
                            client.send(params) { result ->
                                if (untilPersistent && !result.isError) {
                                    obj = result.get()
                                    val message = obj as? Message
                                    if (message?.sendingState?.constructor == MessageSendingStatePending.CONSTRUCTOR) {
                                        pendingMessage[message.id] =
                                            con as CancellableContinuation<Object>
                                    } else {
                                        con.resumeWith(Result.success(obj!!))
                                    }
                                } else {
                                    con.resumeWith(
                                        runCatching {
                                            result.get()
                                        }.onFailure {
                                            log.warn("{}: {}: \n{}", result.error.code, result.error.message, params)
                                        }
                                    )
                                }
                            }
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(timeout)
                            if (con.isActive) {
                                obj?.also {
                                    con.resumeWith(Result.success(it))
                                } ?: run {
                                    con.cancel(CancellationException("Timeout with $timeout"))
                                }
                            }
                        }
                        con.invokeOnCancellation {
                            if (log.isTraceEnabled) {
                                log.warn("Send job cancelled: {}", params)
                            } else {
                                log.warn("Send job cancelled.")
                            }
                        }
                    }
                }.recoverCatching { ex: Throwable ->
                    if (ex.message?.contains("retry after") == true) {
                        val seconds = ex.message!!.substringAfterLast(" ").toLongOrNull() ?: 5
                        log.warn("Wait for {}s", seconds)
                        delay(seconds * 1000)
                        return@recoverCatching send<R>(untilPersistent, timeout, block)
                    } else {
                        throw ex
                    }
                }.getOrThrow()
            }
        }
        if (log.isTraceEnabled) {
            log.trace("in {}, Execute {}", duration, params)
        } else {
            if (duration > 10.seconds) {
                log.warn("Send job cost over 10s: {}", duration)
            }
            log.debug("Execute {} in {}", params::class.simpleName, duration)
        }
        return value as R
    }

    private suspend fun <R : Object> doSend(params: TdApi.Function<R>): TdResult<R> {
        return suspendCancellableCoroutine { con: CancellableContinuation<TdResult<R>> ->
            CoroutineScope(this.coroutineContext).launch {
                client.send(params, { result -> con.resumeWith(Result.success(result)) }) { e: Throwable ->
                    con.resumeWith(Result.failure(e))
                }
            }
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

    suspend fun sendError(message: Message, throwable: Throwable, topic: String = "转发失败") {
        try {
            val errorMsg = "#$topic\n${throwable::class.simpleName}: ${
                throwable.message?.replace(
                    telegramProperties.token,
                    "{token}"
                )
            }"

            send {
                messageText(errorMsg.asFmtText(), message.chatId).apply {
                    this.setReplyToMessageId(message.id)
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
