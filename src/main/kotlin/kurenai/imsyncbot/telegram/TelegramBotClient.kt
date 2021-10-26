package kurenai.imsyncbot.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.command.Command
import kurenai.imsyncbot.config.BotProperties
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.service.ConfigService
import kurenai.imsyncbot.utils.RateLimiter
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.Serializable
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */
@Component
class TelegramBotClient(
    options: DefaultBotOptions,
    private val telegramBotProperties: TelegramBotProperties, //初始化时处理器列表
    private val botProperties: BotProperties,
    private val handlerHolder: HandlerHolder,
    private val commands: List<Command>,
    private val callbacks: List<Callback>,
    private val cacheService: CacheService,
    private val privateChatHandler: PrivateChatHandler,
    private val configService: ConfigService
) : TelegramLongPollingBot(options) {

    private val log = KotlinLogging.logger {}
    private val mapper: ObjectMapper = ObjectMapper()
    private val rateLimiterLock = Object()
    private val manyRequestsLock = Object()
    private var time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    val rateLimiter = RateLimiter(rateLimiterLock, time = time)
//    val fileRateLimiter = RateLimiter(rateLimiterLock, "FileRateLimiter", 0.20, 1, time)

    override fun getBotUsername(): String {
        return telegramBotProperties.username
    }

    override fun getBotToken(): String {
        return telegramBotProperties.token
    }

    override fun onUpdatesReceived(updates: MutableList<Update>) {
        CoroutineScope(Dispatchers.Default).launch {
            updates.forEach { update ->
                try {
                    onUpdateReceivedSuspend(update)
                } catch (e: Exception) {
                    reportError(update, e)
                }
            }
        }
    }

    suspend fun onUpdateReceivedSuspend(update: Update) {
        log.debug("onUpdateReceived: {}", mapper.writeValueAsString(update))
        val message = update.message ?: update.editedMessage ?: update.callbackQuery?.message
        if (message == null) {
            log.debug { "No message" }
            return
        }

        if (botProperties.ban.member.contains(message.from.id)) {
            log.debug("Ignore this message by ban member [${message.from.id}]")
            return
        }
        if (botProperties.ban.group.contains(message.chatId)) {
            log.debug("Ignore this message by ban group [${message.chatId}]")
            return
        }

        if (update.hasCallbackQuery()) {
            for (callback in callbacks) {
                if (callback.handle(update, message)) {
                    return
                }
            }
        }

        if (update.hasMessage() && message.isCommand) {
            val text = message.text
            if (text.startsWith("/help") && message.isUserMessage) {
                val sb = StringBuilder("Command list")
                for (command in commands) {
                    sb.append("\n----------------\n")
                    sb.append("${command.getName()}\n")
                    sb.append(command.getHelp())
                }
                send(
                    SendMessage.builder().chatId(message.chatId.toString()).replyToMessageId(message.messageId)
                        .text(sb.toString()).build()
                )
            } else {
                for (command in commands) {
                    if (command.match(update)) {
                        if (command.execute(update)) {
                            break
                        } else {
                            return
                        }
                    }
                }
            }
        }

        if (message.chatId.equals(privateChatHandler.privateChat)) {
            privateChatHandler.onPrivateChat(update)
            return
        }

        if (update.hasMessage() && (message.isGroupMessage || message.isSuperGroupMessage) ||
            update.hasEditedMessage() && (update.editedMessage.isSuperGroupMessage || update.editedMessage.isGroupMessage)
        ) {
            if (update.hasMessage()) {
                for (handler in handlerHolder.currentTgHandlerList) {
                    if (handler.onMessage(message) == END) break
                }
            } else if (update.hasEditedMessage()) {
                for (handler in handlerHolder.currentTgHandlerList) {
                    if (handler.onEditMessage(update.editedMessage) == END) break
                }
            }
        }
    }

    suspend fun reportError(update: Update, e: Throwable) {
        log.error(e) { e.message }
        try {
            val message = update.message ?: update.editedMessage ?: update.callbackQuery.message
            val msgChatId = message.chatId.toString()
            val msgId = message.messageId

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

            send(SendMessage(msgChatId, "#转发失败\n${e.message}").apply {
                this.replyToMessageId = msgId
                this.replyMarkup =
                    InlineKeyboardMarkup().apply {
                        this.keyboard = listOf(listOf(InlineKeyboardButton("重试").apply { this.callbackData = "retry" }))
                    }
            })
            cacheService.cache(message)
        } catch (e: Exception) {
            log.error(e) { e.message }
        }
    }

    override fun onRegister() {
        try {
            me?.let {
                log.info(
                    "Started telegram-bot: {}({}, {}).",
                    if (it.firstName.equals("null", true)) it.lastName else it.firstName,
                    it.userName,
                    it.id
                )
            } ?: let {
                log.info("Started telegram-bot: {}.", botUsername)
            }
            ContextHolder.telegramBotClient = this
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    override fun onUpdateReceived(update: Update) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                onUpdateReceivedSuspend(update)
            } catch (e: Exception) {
                reportError(update, e)
            }
        }
    }

    @Throws(TelegramApiException::class)
    fun send(sendDocument: SendDocument): Message? {
        return executeFile(sendDocument.document.isNew) {
            super.execute(sendDocument)
        }
    }

    @Throws(TelegramApiException::class)
    fun send(sendPhoto: SendPhoto): Message {
        return executeFile(sendPhoto.photo.isNew) {
            super.execute(sendPhoto)
        }
    }

    @Throws(TelegramApiException::class)
    fun send(sendVideo: SendVideo): Message {
        return executeFile(sendVideo.video.isNew) {
            super.execute(sendVideo)
        }
    }

    @Throws(TelegramApiException::class)
    fun send(sendSticker: SendSticker): Message {
        return executeFile(sendSticker.sticker.isNew) {
            super.execute(sendSticker)
        }
    }

    @Throws(TelegramApiException::class)
    fun send(sendAudio: SendAudio): Message {
        return executeFile(sendAudio.audio.isNew) {
            super.execute(sendAudio)
        }
    }

    @Throws(TelegramApiException::class)
    fun send(sendVoice: SendVoice): Message {
        return executeFile(sendVoice.voice.isNew) {
            super.execute(sendVoice)
        }
    }

    fun send(sendMediaGroup: SendMediaGroup): MutableList<Message> {
        return executeFile(size = sendMediaGroup.medias.size) {
            super.execute(sendMediaGroup)
        }
    }

    fun send(sendAnimation: SendAnimation): Message {
        return executeFile(sendAnimation.animation.isNew) {
            super.execute(sendAnimation)
        }
    }

    @Throws(TelegramApiException::class)
    fun send(sendVideoNote: SendVideoNote): Message {
        return execute(Supplier {
            super.execute(sendVideoNote)
        })
    }

    fun send(editMessageMedia: EditMessageMedia): Serializable {
        return execute(Supplier { super.execute(editMessageMedia) })
    }

    fun <T : Serializable, Method : BotApiMethod<T>> send(method: Method): T {
        return execute(Supplier { super.execute(method) })
    }

    private fun <T> executeFile(isNew: Boolean = true, size: Int = 1, supplier: Supplier<T>): T {
        return awareErrorHandler {
            if (isNew) {
//                fileRateLimiter.acquire(size) {
                    supplier.get()
//                }
            } else {
//                rateLimiter.acquire(size)
                supplier.get()
            }
        }
    }

    private fun <T> execute(supplier: Supplier<T>): T {
        return awareErrorHandler {
//            rateLimiter.acquire {
                supplier.get()
//            }
        }
    }

    private fun <T> awareErrorHandler(executor: () -> T): T {
        return try {
            executor()
        } catch (e: TelegramApiException) {
            val message = e.message
            if (message != null && message.contains("Too Many Requests: retry after")) {
                synchronized(manyRequestsLock) {
                    val manyRequestsWaitTime = message.substring(message.length - 2).trim().toLong() * 1000
                    log.debug { "Wait for many requests ${manyRequestsWaitTime / 1000}s" }
                    time = rateLimiter.now() + manyRequestsWaitTime
                    manyRequestsLock.wait(manyRequestsWaitTime)
                }
                awareErrorHandler(executor)
            } else {
                throw e
            }
        }
    }
}