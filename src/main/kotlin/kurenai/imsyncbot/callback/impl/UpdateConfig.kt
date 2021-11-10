package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.service.ConfigService
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class UpdateConfig(
    private val configService: ConfigService,
    private val baseConfig: BaseConfig
) : Callback() {

    companion object {
        const val methodStr = "updateConfig"
    }

    override val method: String = methodStr

    override fun handle0(update: Update, message: Message): Int {
        val client = ContextHolder.telegramBotClient
        val key = getBody(update)
        val value = configService.get(key)
        client.send(EditMessageText("请输入 `${key.format2Markdown()}` 新的值\n旧值为 `${value?.format2Markdown()}`").apply {
            chatId = message.chatId.toString()
            messageId = message.messageId
            parseMode = ParseMode.MARKDOWNV2
            replyMarkup = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("取消").apply { callbackData = baseConfig.method })))
        })
        waitForMsg(update, message)?.let {
            if (it.hasMessage()) {
                configService.save(key, it.message.text)
                baseConfig.changeToConfigs(message.messageId, message.chatId)
                client.executeAsync(DeleteMessage().apply {
                    chatId = message.chatId.toString()
                    messageId = it.message.messageId
                })
            } else if (!it.hasCallbackQuery()) {
                client.send(SendMessage(message.chatId.toString(), "无法解析消息"))
                baseConfig.changeToConfigs(message.messageId, message.chatId)
            } else {
            }
        }
        return END_WITHOUT_ANSWER
    }
}