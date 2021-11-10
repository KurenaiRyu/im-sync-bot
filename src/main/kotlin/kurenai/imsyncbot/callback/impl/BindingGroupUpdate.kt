package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.repository.BindingGroupRepository
import mu.KotlinLogging
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
class BindingGroupUpdate(
    val repository: BindingGroupRepository,
    val bindingGroupConfig: BindingGroupConfig
) : Callback() {

    private val log = KotlinLogging.logger {}

    companion object {
        const val methodStr = "groupUpdate"
    }

    override val method = methodStr

    override fun handle0(update: Update, message: Message): Int {
        val client = ContextHolder.telegramBotClient
        val params = getBody(update).split(" ")
        val found = when (params[0]) {
            "tg" -> repository.findAll().first { it.tg.toString() == params[1] }
            "qq" -> repository.findAll().first { it.qq.toString() == params[1] }
            else -> {
                log.error { "params error" }
                return END
            }
        }

        if (found == null) {
            client.send(SendMessage(message.chatId.toString(), "未能找到相关绑定群组"))
            return END
        }

        client.send(EditMessageText("修改群组 `${ContextHolder.qqBot.getGroup(found.qq)?.name ?: "?"}`\n请输入 ${params[0]} 新的值\n旧值为 `${params[1]}`").apply {
            chatId = message.chatId.toString()
            messageId = message.messageId
            parseMode = ParseMode.MARKDOWNV2
            replyMarkup = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("取消").apply { callbackData = BindingGroupConfig.methodStr })))
        })

        waitForMsg(update, message)?.let { rec ->
            if (rec.hasMessage()) {
                val text = rec.message.text
                when (params[0]) {
                    "tg" -> found.tg = text.toLong()
                    "qq" -> found.qq = text.toLong()
                }
                repository.save(found)
                bindingGroupConfig.changeToConfigs(message.messageId, message.chatId)
                client.executeAsync(DeleteMessage(message.chatId.toString(), rec.message.messageId))
            } else if (!rec.hasCallbackQuery()) {
                client.send(SendMessage(message.chatId.toString(), "无法解析消息"))
                bindingGroupConfig.changeToConfigs(message.messageId, message.chatId)
            } else {
            }
        }

        return END_WITHOUT_ANSWER
    }
}