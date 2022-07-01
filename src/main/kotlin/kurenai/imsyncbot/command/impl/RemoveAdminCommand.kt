package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.UserConfig
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import mu.KotlinLogging

class RemoveAdminCommand : AbstractTelegramCommand() {

    override val command = "removeAdmin"
    override val help: String = "移除管理员"
    override val onlyGroupMessage = true
    override val onlyReply = true

    private val log = KotlinLogging.logger {}

    override suspend fun execute(update: Update, message: Message): String {
        val reply = message.replyToMessage!!
        val user = reply.from!!
        UserConfig.removeAdmin(user.id)
        return "移除管理员成功"
    }

}