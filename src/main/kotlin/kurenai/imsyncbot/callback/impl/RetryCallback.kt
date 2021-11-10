package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.handler.tg.TgForwardHandler
import kurenai.imsyncbot.service.CacheService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class RetryCallback(val cacheService: CacheService, val forwardHandler: TgForwardHandler) : Callback() {

    private val log = KotlinLogging.logger {}

    override val method: String = "retry"

    override fun handle0(update: Update, message: Message): Int {
        val client = ContextHolder.telegramBotClient

        val originMessage = cacheService.getTg(message.chatId, message.replyToMessage.messageId)
        if (originMessage == null) {
            client.send(EditMessageText("转发失败：缓存中无法找到该条消息，无法重试").apply {
                this.chatId = chatId
                this.messageId = messageId
            })
            return END
        }

        val messageId = message.messageId
        val chatId = message.chatId.toString()
        val retryMsg = "${message.text}\n\n正在重试..."
        client.send(EditMessageText(retryMsg).apply {
            this.chatId = chatId
            this.messageId = messageId
        })

        try {
            suspend {
                forwardHandler.onMessage(originMessage)
            }
            client.send(DeleteMessage(chatId, messageId))
        } catch (e: Exception) {
            log.error(e) { e.message }
            client.send(EditMessageText("#转发失败\n${e.message}").apply {
                this.chatId = chatId
                this.messageId = messageId
                this.replyMarkup =
                    InlineKeyboardMarkup().apply {
                        this.keyboard = listOf(listOf(InlineKeyboardButton("重试").apply { this.callbackData = "retry" }))
                    }
            })
        }

        return END
    }
}