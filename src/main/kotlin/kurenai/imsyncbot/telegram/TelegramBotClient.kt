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
import kurenai.imsyncbot.utils.RateLimiter
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
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
) : TelegramLongPollingBot(options) {

    private val log = KotlinLogging.logger {}
    private val mapper: ObjectMapper = ObjectMapper()
    private val rateLimiterLock = Object()
    private val time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    val rateLimiter = RateLimiter(rateLimiterLock, time = time)
    val fileRateLimiter = RateLimiter(rateLimiterLock, "FileRateLimiter", 0.25, 10, time)

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
        CoroutineScope(Dispatchers.IO).launch {
            log.debug("onUpdateReceived: {}", mapper.writeValueAsString(update))
        }
        val message = update.message ?: update.editedMessage ?: update.callbackQuery.message

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
                execute(
                    SendMessage.builder().chatId(message.chatId.toString()).replyToMessageId(message.messageId)
                        .text(sb.toString()).build()
                )
            } else {
                for (command in commands) {
                    if (command.match(text)) {
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

    fun reportError(update: Update, e: Throwable) {
        log.error(e) { e.message }
        try {
            ContextHolder.masterChatId.takeIf { it != 0L }?.let {
                val message = update.message ?: update.editedMessage ?: update.callbackQuery.message
                val masterChatId = it.toString()
                val msgChatId = message.chatId.toString()
                val msgId = message.messageId
                val simpleMsg = "#转发失败\n${e.message}\n\nhttps://t.me/c/${
                    msgChatId.let { id ->
                        if (id.startsWith("-100")) {
                            id.substring(4)
                        } else id
                    }
                }/$msgId"
                val recMsgId = execute(SendMessage(masterChatId, simpleMsg)).messageId
                execute(EditMessageText.builder().chatId(masterChatId).messageId(recMsgId).text("$simpleMsg\n\n${mapper.writeValueAsString(update)}").build())

                execute(SendMessage(msgChatId, "#转发失败\n${e.message}").apply {
                    this.replyToMessageId = msgId
                    this.replyMarkup =
                        InlineKeyboardMarkup().apply {
                            this.keyboard = listOf(listOf(InlineKeyboardButton("重试").apply { this.callbackData = "retry" }))
                        }
                })
                cacheService.cache(message)
            }
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

    override fun execute(sendMediaGroup: SendMediaGroup): MutableList<Message> {
        return executeFile(size = sendMediaGroup.medias.size) {
            super.execute(sendMediaGroup)
        }
    }

    override fun execute(sendAnimation: SendAnimation): Message {
        val supplier = Supplier {
            super.execute(sendAnimation)
        }
        return if (sendAnimation.animation.isNew) {
            fileRateLimiter.acquire {
                supplier.get()
            }
        } else {
            rateLimiter.acquire()
            supplier.get()
        }
    }

    @Throws(TelegramApiException::class)
    fun send(sendVideoNote: SendVideoNote): Message {
        rateLimiter.acquire()
        return super.execute(sendVideoNote)
    }


    override fun <T : Serializable, Method : BotApiMethod<T>> execute(method: Method): T {
        rateLimiter.acquire()
        return super.execute(method)
    }

    override fun execute(editMessageMedia: EditMessageMedia): Serializable {
        fileRateLimiter.acquire()
        return super.execute(editMessageMedia)
    }

    private fun <T> executeFile(isNew: Boolean = true, size: Int = 1, supplier: Supplier<T>): T {
        return if (isNew) {
            fileRateLimiter.acquire(size) {
                supplier.get()
            }
        } else {
            rateLimiter.acquire(size)
            supplier.get()
        }
    }
}