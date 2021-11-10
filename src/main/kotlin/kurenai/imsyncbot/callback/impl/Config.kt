package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.utils.BotUtil
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class Config : Callback() {

    companion object {
        val messageIds = HashMap<Long, Int>()
        const val methodStr = "config"
    }

    override val method = methodStr

    override fun handle0(update: Update, message: Message): Int {
        changeToConfigs(message.messageId, message.chatId)
        return END
    }

    fun changeToConfigs(messageId: Int, chatId: Long) {
        ContextHolder.telegramBotClient.send(EditMessageText().apply {
            text = "请选择配置项"
            this.messageId = messageIds.getOrDefault(chatId, messageId)
            this.chatId = chatId.toString()
            replyMarkup = InlineKeyboardMarkup(buildMarkup())
        })
    }

    fun changeToConfigs(update: Update) {
        val client = ContextHolder.telegramBotClient
        val chatId = update.message.chatId
        if (messageIds.contains(chatId)) {
            client.executeAsync(DeleteMessage(chatId.toString(), messageIds[chatId]!!))
        }
        val rec = ContextHolder.telegramBotClient.send(SendMessage().apply {
            text = "请选择配置项"
            this.chatId = chatId.toString()
            replyMarkup = InlineKeyboardMarkup(buildMarkup())
        })

        messageIds[chatId] = rec.messageId
    }

    fun buildMarkup(): List<List<InlineKeyboardButton>> {
        return BotUtil.buildInlineMarkup(
            listOf(
                mapOf("基础配置" to BaseConfig.methodStr),
                mapOf("群组配置" to BindingGroupConfig.methodStr)
            )
        )
    }
}