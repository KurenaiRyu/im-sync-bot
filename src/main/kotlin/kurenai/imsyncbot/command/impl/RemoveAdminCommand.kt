package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.UserConfig
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class RemoveAdminCommand : AbstractCommand() {

    override val command = "removeAdmin"
    override val help: String = "移除管理员"
    override val onlyGroupMessage = true
    override val onlyReply = true

    private val log = KotlinLogging.logger {}

    override fun execute(update: Update, message: Message): String {
        val user = message.replyToMessage.from
        UserConfig.removeAdmin(user.id)
        return "移除管理员成功"
    }

}