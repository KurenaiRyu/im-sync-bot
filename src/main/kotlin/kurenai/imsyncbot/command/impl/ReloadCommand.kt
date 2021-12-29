package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class ReloadCommand : AbstractCommand() {

    override val command = "reload"
    override val help: String = "重新加载config目录下的配置"

    override fun execute(update: Update, message: Message): String {
        GroupConfig.reload()
        UserConfig.reload()
        return "已重新加载配置"
    }
}