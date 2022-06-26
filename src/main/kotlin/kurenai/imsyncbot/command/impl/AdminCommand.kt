package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.UserConfig
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import mu.KotlinLogging

class AdminCommand : AbstractTelegramCommand() {

    override val command = "admin"
    override val help: String = "设置管理员"
    override val onlyGroupMessage = true

    private val log = KotlinLogging.logger {}

    override fun execute(update: Update, message: Message): String {
        return if (message.isReply()) {
            val user = message.replyToMessage!!.from!!
            UserConfig.admin(user.id, username = user.username)
            "添加管理员成功"
        } else {
            "需要引用一条消息来找到该用户"
        }
    }

}