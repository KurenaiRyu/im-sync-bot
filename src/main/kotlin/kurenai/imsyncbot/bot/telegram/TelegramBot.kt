package kurenai.imsyncbot.bot.telegram

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.asCache
import it.tdlight.client.*
import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kurenai.imsyncbot.*
import kurenai.imsyncbot.bot.telegram.TgMessageHandler.ListenerResult
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.service.FileService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.getLogger
import kurenai.imsyncbot.utils.telegram.*
import kurenai.imsyncbot.utils.withVT
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import it.tdlight.jni.TdApi.Function as TdFunction

/**
 * Telegram 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */

lateinit var defaultTelegramBot: TelegramBot

class TelegramBot(
    botProperties: BotProperties,
    coroutineContext: CoroutineContext,
) {

    companion object {
        val log = getLogger()
        val DEFAULT_TIMEOUT = 10.seconds
    }

    private lateinit var apiToken: APIToken

    // Configure the client
    private lateinit var settings: TDLibSettings

    private lateinit var client: SimpleTelegramClient

    private val telegramProperties = botProperties.telegram
    private val tgScope = CoroutineScope(
        SupervisorJob(coroutineContext[Job]) +
                CoroutineName("TelegramBot") +
                CoroutineExceptionHandler { context, ex ->
                    when (ex) {
                        is CancellationException -> {
                            log.warn("{} was cancelled", context[CoroutineName])
                        }

                        else -> {
                            log.warn("with {}", context[CoroutineName], ex)
                        }
                    }
                }
    )

    internal val messageHandler: TgMessageHandler = TgMessageHandler(botProperties, tgScope)

    val status = MutableStateFlow<BotStatus>(Initializing)
    val token: String = botProperties.telegram.token

    val disposableHandlers = LinkedList<TelegramDisposableHandler>()

    val editedMessages = Caffeine.newBuilder()
        .maximumSize(50)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .asCache<String, Boolean>()

    val pendingMessage = Caffeine.newBuilder()
        .maximumSize(50)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .asCache<Long, CompletableDeferred<TdApi.Message>>()

    context(bot: ImSyncBot)
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
            isMessageDatabaseEnabled = false
        }
        client =
            SimpleTelegramClientFactory().builder(settings).build(AuthenticationSupplier.bot(telegramProperties.token))
        client.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) { update ->
            if (update.authorizationState.constructor == AuthorizationStateReady.CONSTRUCTOR) {
                log.info("Telegram bot started.")
                status.update { Running }
                if (!::defaultTelegramBot.isInitialized) defaultTelegramBot = this@TelegramBot
                client.addUpdatesHandler {
                    with(bot) { messageHandler.handle(it) }
                }
                tgScope.launch {
                    updateCommand()
                }
            }
        }


    }

    fun <R : TdApi.Object?, Event : TdApi.Update> addListener(
        timeout: Duration? = 5L.seconds,
        matchBlock: ((TdApi.Update) -> Boolean)? = null,
        handleBlock: (Event) -> ListenerResult<R>
    ): Deferred<R> {
        return messageHandler.addListener(timeout, matchBlock, handleBlock)
    }

    suspend inline fun sendMessageText(
        text: String,
        chatId: Long,
        parseMode: ParseMode = ParseMode.TEXT,
        replayToMessageId: Long = 0,
        messageThreadId: Long = 0,
        untilPersistent: Boolean = false,
    ) = sendMessageText(text.fmt(parseMode), chatId, replayToMessageId, messageThreadId, untilPersistent)

    suspend inline fun sendMessageText(
        formattedText: TdApi.FormattedText,
        chatId: Long,
        replyToMessageId: Long = 0,
        messageThreadId: Long = 0,
        untilPersistent: Boolean = false,
    ): TdApi.Message = send(untilPersistent) {
        messageText(formattedText, chatId).apply {
            this.replyToMessageId = replyToMessageId
            this.messageThreadId = messageThreadId
        }
    }

    suspend inline fun sendMessagePhoto(
        url: String,
        text: String,
        chatId: Long,
        parseMode: ParseMode = ParseMode.TEXT,
        replayToMessageId: Long = 0,
        messageThreadId: Long = 0,
        untilPersistent: Boolean = false,
    ) = sendMessagePhoto(
        url,
        text.fmt(parseMode),
        chatId,
        replayToMessageId,
        messageThreadId,
        untilPersistent
    )

    suspend fun sendMessagePhoto(
        url: String,
        formattedText: FormattedText,
        chatId: Long,
        replyToMessageId: Long = 0,
        messageThreadId: Long = 0,
        untilPersistent: Boolean = false,
    ): Message {
        SendMessage().apply {
            this.replyToMessageId = replyToMessageId
            this.messageThreadId = messageThreadId
            this.inputMessageContent = InputMessagePhoto().apply {
                this.photo = FileService.download(url).inputFile
                this.caption = formattedText
            }
        }
        return send(untilPersistent = untilPersistent) {
            messageText(formattedText, chatId).apply {
                this.replyToMessageId = replyToMessageId
                this.messageThreadId = messageThreadId
            }
        }
    }

    suspend fun sendMessagePhoto(
        data: ByteArray,
        formattedText: FormattedText,
        chatId: Long,
        filename: String = "${System.currentTimeMillis()}",
        replyToMessageId: Long = 0,
        untilPersistent: Boolean = false,
    ) = send(untilPersistent = untilPersistent) {
        val path = Path.of(BotUtil.getImagePath(filename))
        path.writeBytes(data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        SendMessage().apply {
            this.chatId = chatId
            this.replyToMessageId = replyToMessageId
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
        replyToMessageId: Long = 0,
        untilPersistent: Boolean = false,
    ) = send(untilPersistent = untilPersistent) {
        val path = Path.of(BotUtil.getDocumentPath(filename))
        path.writeBytes(data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        SendMessage().apply {
            this.chatId = chatId
            this.replyToMessageId = replyToMessageId
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
        return if (synchronous) withVT { send(params = downloadFile) }
        else send(params = downloadFile)
    }

    suspend inline fun getMessage(chatId: Long, messageId: Long): Message = send {
        GetMessage(chatId, messageId)
    }

    suspend inline fun getChatMember(chatId: Long, sender: MessageSender) = send {
        GetChatMember(chatId, sender)
    }

    suspend inline fun getUser(userId: Long) = send {
        GetUser(userId)
    }

    suspend inline fun getUser(message: Message): User = send {
        GetUser(message.senderUserId)
    }

    fun getMe(): User = client.me

    suspend inline fun getChat(chatId: Long) = send {
        GetChat(chatId)
    }

    fun getUsername(): String = client.me.usernames.activeUsernames.first()

    @OptIn(ExperimentalTime::class)
    suspend inline fun <R : Object> send(
        untilPersistent: Boolean = false,
        timeout: Duration = DEFAULT_TIMEOUT,
        crossinline block: () -> TdFunction<R>
    ): R {
        return send(untilPersistent, timeout, block())
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <R: Object> send(
        untilPersistent: Boolean = false,
        timeout: Duration = DEFAULT_TIMEOUT,
        params: TdFunction<R>
    ): R {
        var deferred: CompletableDeferred<Message>? = null
        var result: R? = null
        while (status.value is Running) {
            try {
                return withTimeout(timeout) {
                    result = client.sendSuspend(params)

                    if (untilPersistent && result is Message &&
                        result.sendingState.constructor == MessageSendingStatePending.CONSTRUCTOR
                    ) {
                        deferred = CompletableDeferred()
                        pendingMessage[result.id] = deferred
                        deferred.await() as R
                    } else {
                        result
                    }
                }
            } catch (ex: Throwable) {
                if (ex.message?.contains("retry after") == true) {
                    val seconds = ex.message!!.substringAfterLast(" ").toLongOrNull() ?: 5
                    log.warn("Wait for {}s", seconds)
                    delay(seconds * 1000)
                    continue
                } else {
                    deferred?.completeExceptionally(ex)
                    throw ex
                }
            } finally {
                if (deferred?.isCompleted ?: false && result != null && result is Message) {
                    pendingMessage.invalidate(result.id)
                }
            }
        }
        throw BotException("Telegram not in running: ${status.value}")
    }

    suspend inline fun <R : Object> SimpleTelegramClient.sendSuspend(
        params: TdFunction<R>
    ): R = suspendCancellableCoroutine { cont ->
        send<R>(params) { result ->
            runCatching { result.get() }
                .onSuccess { cont.resume(it) }
                .onFailure { cont.resumeWithException(it) }
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

    private data class TdRequest<R : TdApi.Object>(
        val untilPersistent: Boolean = false,
        val timeout: Duration = DEFAULT_TIMEOUT,
        val params: TdFunction<R>
    )
}
