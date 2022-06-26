package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.GroupConfig
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update

class DefaultCommand : AbstractTelegramCommand() {

    override val command = "default"
    override val help: String = "设置默认群"
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String {
        GroupConfig.default(message)
        return "设置默认群成功"
    }

}