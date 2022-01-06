package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.UserConfig
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class StartCommand : AbstractCommand() {

    override val help = "触发机器人初始化一些参数，如记录主人聊天id等"
    override val command = "start"
    override val onlyUserMessage = true
    override val onlyMaster = true

    override fun execute(update: Update, message: Message): String {
        UserConfig.master(message)
        return "Hello, my master! \n\n" +
                "现已更新主人的chatId[${message.chatId}]以及username[${message.from.userName}]，当单独AT机器人的时候，转发到tg上时则会替换为主人username。\n" +
                "另外可以使用 /help 查看各个命令的帮助。"
    }
}