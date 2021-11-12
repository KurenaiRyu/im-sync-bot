package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.domain.BindingGroup
import kurenai.imsyncbot.repository.BindingGroupRepository
import kurenai.imsyncbot.utils.BotUtil
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class BindingGroupConfig(
    val repository: BindingGroupRepository
) : Callback() {

    val lock = Object()

    companion object {
        const val methodStr = "bindingGroupConfig"
    }

    override val method = methodStr

    override fun handle0(update: Update, message: Message): Int {
        changeToConfigs(message.messageId, message.chatId)
        return END
    }

    fun changeToConfigs(messageId: Int, chatId: Long) {
        val all = repository.findAll()
        flushBinding(all)

        val markup = ArrayList<List<InlineKeyboardButton>>()
        markup.addAll(
            BotUtil.buildInlineMarkup(
                listOf(
                    mapOf("新增" to BindingGroupAdd.methodStr),
                    mapOf("返回" to Config.methodStr)
                )
            )
        )
        markup.addAll(BotUtil.buildInlineMarkup(all.map { bindingGroup ->
            mapOf(
                (ContextHolder.qqBot.getGroup(bindingGroup.qq)?.name ?: "?") to "${BindingGroupUpdate.methodStr} qq ${bindingGroup.qq}",
                "qq: ${bindingGroup.qq}" to "${BindingGroupUpdate.methodStr} qq ${bindingGroup.qq} ",
                "tg: ${bindingGroup.tg}" to "${BindingGroupUpdate.methodStr} tg ${bindingGroup.tg}",
                "解除绑定" to "${BindingGroupDelete.methodStr} ${bindingGroup.qq}",
            )
        }))

        ContextHolder.telegramBotClient.send(EditMessageText().apply {
            text = "每一行是一个配置项，点击配置项更新或者清除。"
            this.messageId = Config.messageIds.getOrDefault(chatId, messageId)
            this.chatId = chatId.toString()
            replyMarkup = InlineKeyboardMarkup(markup)
        })
    }

    fun flushBinding(all: MutableList<BindingGroup>) {
        synchronized(lock) {
            val tgQQBinding = ContextHolder.tgQQBinding
            val qqTgBinding = ContextHolder.qqTgBinding
            tgQQBinding.clear()
            qqTgBinding.clear()
            for (bindingGroup in all) {
                tgQQBinding[bindingGroup.tg] = bindingGroup.qq
                qqTgBinding[bindingGroup.qq] = bindingGroup.tg
            }
        }
    }
}