package kurenai.mybot.callback.impl

import kurenai.mybot.ContextHolder
import kurenai.mybot.callback.Callback
import kurenai.mybot.handler.impl.ForwardHandler
import kurenai.mybot.service.CacheService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class RetryCallback(val cacheService: CacheService, val forwardHandler: ForwardHandler) : Callback {

    override suspend fun handle(update: Update, message: Message): Boolean {
        if ("retry" != update.callbackQuery.data) {
            return false
        }
        val client = ContextHolder.telegramBotClient

        val originMessage = cacheService.getTg(message.replyToMessage.messageId)
        if (originMessage == null) {
            client.execute(EditMessageText("转发失败：缓存中无法找到该条消息，无法重试").apply {
                this.chatId = chatId
                this.messageId = messageId
            })
            return true
        }

        val messageId = message.messageId
        val chatId = message.chatId.toString()
        val retryMsg = "${message.text}\n\n正在重试..."
        client.execute(EditMessageText(retryMsg).apply {
            this.chatId = chatId
            this.messageId = messageId
        })

        try {
            forwardHandler.handleTgMessage(originMessage)
            client.execute(DeleteMessage(chatId, messageId))
        } catch (e: Exception) {
            client.execute(EditMessageText("#转发失败\n${e.message}").apply {
                this.chatId = chatId
                this.messageId = messageId
                this.replyMarkup =
                    InlineKeyboardMarkup().apply {
                        this.keyboard = listOf(listOf(InlineKeyboardButton("重试").apply { this.callbackData = "retry" }))
                    }
            })
        }

        return true
    }
}