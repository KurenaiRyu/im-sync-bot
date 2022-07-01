package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.UserConfig
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import mu.KotlinLogging

class SuperAdminCommand : AbstractTelegramCommand() {

    override val command = "superAdmin"
    override val help: String = "设置超级管理员"
    override val onlyGroupMessage = true

    private val log = KotlinLogging.logger {}

    override suspend fun execute(update: Update, message: Message): String {
        return if (message.isReply()) {
            val user = message.replyToMessage!!.from!!
            if (user.isBot) "机器人无法成为管理员"
            else {
                UserConfig.admin(user.id, isSuper = true, username = user.username)
                "添加超级管理员成功"
            }
        } else {
            "需要引用一条消息来找到该用户"
        }
    }

}