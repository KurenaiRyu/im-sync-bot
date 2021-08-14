package kurenai.mybot.command.impl

import kurenai.mybot.BotConfigConstant
import kurenai.mybot.ContextHolder
import kurenai.mybot.command.Command
import kurenai.mybot.domain.BotConfig
import kurenai.mybot.repository.BotConfigRepository
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class StartCommand(val botConfigRepository: BotConfigRepository) : Command {

    override fun execute(update: Update): Boolean {
        if (update.hasMessage()) {
            val message = update.message
            if (message.isUserMessage && message.from.id == ContextHolder.masterOfTg) {
                botConfigRepository.save(BotConfig(BotConfigConstant.MASTER_CHAT_ID, message.chatId.toString()))
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