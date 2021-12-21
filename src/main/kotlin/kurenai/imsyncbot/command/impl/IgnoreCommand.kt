package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.GroupConfig
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class IgnoreCommand : AbstractCommand() {

    override val command = "ignore"
    override val help: String = "忽略群信息，但是一些事件仍然会发送至该群组"
    override val onlyUserMessage = false

    override fun execute(update: Update, message: Message): String {
        GroupConfig.ban(message.chatId)
        return "忽略群信息设置成功"
    }

}