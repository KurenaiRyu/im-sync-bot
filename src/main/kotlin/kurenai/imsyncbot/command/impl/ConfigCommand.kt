package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.callback.impl.Config
import kurenai.imsyncbot.command.Command
import kurenai.imsyncbot.service.ConfigService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class ConfigCommand(
    val configService: ConfigService,
    val config: Config
) : Command() {

    override val command = "config"
    override val help = "机器人配置相关"

    override fun execute(update: Update): Boolean {
        config.changeToConfigs(update)
        return true
    }
}