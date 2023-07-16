package kurenai.imsyncbot.bot.telegram

import com.sksamuel.aedile.core.caffeineBuilder
import it.tdlight.client.*
import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kurenai.imsyncbot.*
import kurenai.imsyncbot.bot.qq.QQBot
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
    }

    private lateinit var apiToken: APIToken

    // Configure the client
    private lateinit var settings: TDLibSettings

    private lateinit var client: SimpleTelegramClient

    private val messageHandler: TgMessageHandler = TgMessageHandler(bot)

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

    val pendingMessage = caffeineBuilder<Long, CancellableContinuation<it.tdlight.client.Result<Object>>> {
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

    suspend fun <R : Object> send(
        untilPersistent: Boolean = false,
        timeout: Duration = 5.seconds,
        block: suspend () -> TdApi.Function<R>
    ): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }

        return withContext(this.coroutineContext) {
            val result = suspendCancellableCoroutine<it.tdlight.client.Result<R>> { con ->
                launch {
                    client.send(block.invoke()) { result ->
                        if (untilPersistent && !result.isError) {
                            val obj = result.get()
                            if ((obj as? Message)?.sendingState?.constructor == MessageSendingStatePending.CONSTRUCTOR) {
                                pendingMessage[obj.id] =
                                    con as CancellableContinuation<it.tdlight.client.Result<Object>>
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
            return@withContext if (result.isError && result.error.message.contains("retry after")) {
                val seconds = result.error.message.substringAfterLast(" ").toLongOrNull() ?: 200
                delay(seconds * 1000)
                send(untilPersistent, timeout, block)
            } else {
                result.get()
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
