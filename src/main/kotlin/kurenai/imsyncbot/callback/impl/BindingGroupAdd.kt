package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.domain.BindingGroup
import kurenai.imsyncbot.repository.BindingGroupRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class BindingGroupAdd(
    val repository: BindingGroupRepository,
    val bindingGroupConfig: BindingGroupConfig
) : Callback() {

    private val log = KotlinLogging.logger {}

    companion object {
        const val methodStr = "groupAdd"
    }

    override val method = methodStr

    override fun handle0(update: Update, message: Message): Int {
        val client = ContextHolder.telegramBotClient

        client.send(EditMessageText("请输入qq群号以及tg群id，用分号分隔 <qq>:<tg>\n例如 123456789:987654321").apply {
            chatId = message.chatId.toString()
            messageId = message.messageId
            replyMarkup = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("取消").apply { callbackData = BindingGroupConfig.methodStr })))
        })
        waitForMsg(update, message)?.let { rec ->
            if (rec.hasMessage()) {
                val params = rec.message.text.split(":")
                val qq = params[0].toLong()
                val tg = params[1].toLong()
                val found = repository.findAll().takeIf { it.isNotEmpty() }?.first { it.qq == qq || it.tg == tg }
                if (found == null) {
                    repository.save(BindingGroup(qq, params[1].toLong()))
                } else {
                    found.qq = qq
                    found.tg = tg
                    repository.save(found)
                }
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