package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.UserConfig
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class SuperAdminCommand : AbstractCommand() {

    override val command = "superAdmin"
    override val help: String = "设置超级管理员"
    override val onlyGroupMessage = true

    private val log = KotlinLogging.logger {}

    override fun execute(update: Update, message: Message): String {
        return if (message.isReply) {
            val user = message.replyToMessage.from
            if (user.isBot) "机器人无法成为管理员"
            else {
                UserConfig.admin(user.id, isSuper = true, username = user.userName)
                "添加超级管理员成功"
            }
        } else {
            "需要引用一条消息来找到该用户"
        }
    }

}