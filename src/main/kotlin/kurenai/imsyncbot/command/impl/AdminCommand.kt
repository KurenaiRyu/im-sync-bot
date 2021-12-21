package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.AdminConfig
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class AdminCommand : AbstractCommand() {

    override val command = "admin"
    override val help: String = "设置管理员"
    override val onlyGroupMessage = true

    private val log = KotlinLogging.logger {}

    override fun execute(update: Update, message: Message): String {
        return if (message.isReply) {
            val user = message.replyToMessage.from
            AdminConfig.add(user.id)
            "添加管理员成功"
        } else {
            "需要引用一条消息来找到该用户"
        }
    }

}