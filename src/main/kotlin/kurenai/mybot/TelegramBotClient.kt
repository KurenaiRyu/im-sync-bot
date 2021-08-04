package kurenai.mybot

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.mybot.handler.config.ForwardHandlerProperties
import kurenai.mybot.telegram.TelegramBotProperties
import mu.KotlinLogging
import org.springframework.context.ApplicationContext
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.ForwardMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

/**
 * 机器人实例
 * @author Kurenai
 * @since 2021-06-30 14:05
 */
class TelegramBotClient(
    options: DefaultBotOptions,
    private val telegramBotProperties: TelegramBotProperties, //初始化时处理器列表
    private val forwardProperties: ForwardHandlerProperties,
    private val handlerHolder: HandlerHolder,
    val context: ApplicationContext,
) : TelegramLongPollingBot(options) {

    private val log = KotlinLogging.logger {}
    private val mapper: ObjectMapper = ObjectMapper()

    override fun getBotUsername(): String {
        return telegramBotProperties.username
    }

    override fun getBotToken(): String {
        return telegramBotProperties.token
    }

    override fun onUpdateReceived(update: Update) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                log.debug("onUpdateReceived: {}", mapper.writeValueAsString(update))
            } catch (e: JsonProcessingException) {
                log.debug("onUpdateReceived: {}", update)
            }
            if (update.hasMessage() && (update.message.isGroupMessage || update.message.isSuperGroupMessage) ||
                update.hasEditedMessage() && (update.editedMessage.isSuperGroupMessage || update.editedMessage.isGroupMessage)
            ) {
                val qqBotClient = context.getBean(QQBotClient::class.java)
                if (update.hasMessage()) {
                    for (handler in handlerHolder.currentHandlerList) {
                        try {
                            if (!handler.handleMessage(this@TelegramBotClient, qqBotClient, update, update.message)) break
                        } catch (e: Exception) {
                            log.error(e.message, e)
                            val chatId = forwardProperties.group.defaultTelegram.toString()
                            val messageId = execute(
                                ForwardMessage.builder()
                                    .fromChatId(update.message.chatId.toString())
                                    .messageId(update.message.messageId)
                                    .chatId(chatId)
                                    .build()).messageId
                            execute(SendMessage.builder().chatId(chatId).text("转发失败: ${e.message}").replyToMessageId(messageId).build())
                        }
                    }
                } else if (update.hasEditedMessage()) {
                    for (handler in handlerHolder.currentHandlerList) {
                        try {
                            if (!handler.handleEditMessage(this@TelegramBotClient, qqBotClient, update, update.editedMessage)) break
                        } catch (e: Exception) {
                            log.error(e.message, e)
                        }
                    }
                }
            }
        }
    }

    override fun onRegister() {
        try {
            me?.let {
                log.info("Started telegram-bot: {}({}, {}).", if (it.firstName.equals("null", true)) it.lastName else it.firstName, it.userName, it.id)
            } ?: let {
                log.info("Started telegram-bot: {}.", botUsername)
            }
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }
}