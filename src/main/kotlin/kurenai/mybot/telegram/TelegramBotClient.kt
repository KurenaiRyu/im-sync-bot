package kurenai.mybot.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.mybot.ContextHolder
import kurenai.mybot.HandlerHolder
import kurenai.mybot.command.Command
import kurenai.mybot.config.BotProperties
import kurenai.mybot.utils.RetryUtil
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

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
    private val commands: List<Command>
) : TelegramLongPollingBot(options) {

    private val log = KotlinLogging.logger {}
    private val mapper: ObjectMapper = ObjectMapper()

    override fun getBotUsername(): String {
        return telegramBotProperties.username
    }

    override fun getBotToken(): String {
        return telegramBotProperties.token
    }

    override fun onUpdatesReceived(updates: MutableList<Update>) {
        CoroutineScope(Dispatchers.Default).launch {
            updates.forEach { update ->
                RetryUtil.aware({ onUpdateReceivedSuspend(update) }, { _, e ->
                    e?.let {
                        reportError(update, e)
                    }
                })
            }
        }
    }

    suspend fun onUpdateReceivedSuspend(update: Update) {
        CoroutineScope(Dispatchers.IO).launch {
            log.debug("onUpdateReceived: {}", mapper.writeValueAsString(update))
        }
        val message = update.message ?: update.editedMessage

        if (botProperties.ban.member.contains(message.from.id)) {
            log.debug("Ignore this message by ban member [${message.from.id}]")
            return
        }
        if (botProperties.ban.group.contains(message.chatId)) {
            log.debug("Ignore this message by ban group [${message.chatId}]")
            return
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


        if (update.hasMessage() && (message.isGroupMessage || message.isSuperGroupMessage) ||
            update.hasEditedMessage() && (update.editedMessage.isSuperGroupMessage || update.editedMessage.isGroupMessage)
        ) {
            if (update.hasMessage()) {
                for (handler in handlerHolder.currentHandlerList) {
                    if (!handler.handleTgMessage(update, message)) break
                }
            } else if (update.hasEditedMessage()) {
                for (handler in handlerHolder.currentHandlerList) {
                    try {
                        if (!handler.handleTgEditMessage(update, update.editedMessage)) break
                    } catch (e: Exception) {
                        log.error(e.message, e)
                    }
                }
            }
        }
    }

    fun reportError(update: Update, e: Throwable) {
        ContextHolder.masterChatId.takeIf { it != 0L }?.let {
            val chatId = it.toString()
            val msgChatId = (update.message?.chatId ?: update.editedMessage.chatId).toString().substring(4)
            val msgId = update.message?.messageId ?: update.editedMessage.messageId
            val simpleMsg = "#ForwardError 转发失败: ${e.message}\n\nhttps://t.me/c/$msgChatId/$msgId"
            val recMsgId = execute(SendMessage(chatId, simpleMsg)).messageId
            execute(EditMessageText.builder().chatId(chatId).messageId(recMsgId).text("$simpleMsg\n\n${mapper.writeValueAsString(update)}").build())
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
            RetryUtil.aware({ onUpdateReceivedSuspend(update) }, { _, e ->
                e?.let {
                    reportError(update, e)
                }
            })
        }
    }

//    fun get
}