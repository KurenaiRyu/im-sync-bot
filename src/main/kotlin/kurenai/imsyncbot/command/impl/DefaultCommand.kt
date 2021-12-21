package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractCommand
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class DefaultCommand : AbstractCommand() {

    override val command = "default"
    override val help: String = "设置默认群"
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String {
        return "设置默认群成功"
    }

}