package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.BotConfigConstant
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.Command
import kurenai.imsyncbot.domain.BotConfig
import kurenai.imsyncbot.repository.BotConfigRepository
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class StartCommand(val botConfigRepository: BotConfigRepository) : Command {

    override fun execute(update: Update): Boolean {
        if (update.hasMessage()) {
            val message = update.message
            if (message.isUserMessage && message.from.id == ContextHolder.masterOfTg) {
                val chatId = message.chatId.toString()
                botConfigRepository.save(BotConfig(BotConfigConstant.MASTER_CHAT_ID, chatId))
                ContextHolder.masterChatId = message.chatId
                ContextHolder.telegramBotClient.execute(
                    SendMessage.builder().chatId(chatId)
                        .text("Hello, my master! \n\n现已记录主人的聊天id，之后tg上的错误将会转发至这个私聊当中。\n另外可以使用 /help 查看各个命令的帮助。").build()
                )
            }
        }
        return false
    }

    override fun match(text: String): Boolean {
        return text.startsWith("/start")
    }

    override fun getHelp(): String {
        return "/start 触发机器人初始化一些参数，如记录主人聊天id等"
    }
}