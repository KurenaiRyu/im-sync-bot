package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.BotConfigKey
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.Command
import kurenai.imsyncbot.domain.BotConfig
import kurenai.imsyncbot.service.ConfigService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class StartCommand(val configService: ConfigService) : Command() {

    override val help = "触发机器人初始化一些参数，如记录主人聊天id等"
    override val command = "start"

    override fun execute(update: Update): Boolean {
        val client = ContextHolder.telegramBotClient
        val message = update.message
        val chatId = message.chatId.toString()
        configService.saveAll(
            listOf(
                BotConfig(BotConfigKey.MASTER_ID, message.from.id),
                BotConfig(BotConfigKey.MASTER_USERNAME, message.from.userName),
                BotConfig(BotConfigKey.MASTER_CHAT_ID, chatId)
            )
        )
        ContextHolder.masterChatId = update.message.chatId
        client.send(
            SendMessage.builder().chatId(chatId)
                .text("Hello, my master! \n\n现已记录主人的聊天id，之后tg上的错误将会转发至这个私聊当中。\n另外可以使用 /help 查看各个命令的帮助。").build()
        )
        return true
    }
}