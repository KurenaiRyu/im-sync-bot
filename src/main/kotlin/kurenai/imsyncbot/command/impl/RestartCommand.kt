package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.Command
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update
import kotlin.system.exitProcess

@Component
class RestartCommand : Command() {

    private val log = KotlinLogging.logger {}

    override val help = "重启"
    override val command = "restart"

    override fun execute(update: Update): Boolean {
        ContextHolder.telegramBotClient.sendMessage(update.message.chatId, "准备重启，请发送 /start 或者其他命令以确认重启完成")
        ContextHolder.qqBot.close()
        ContextHolder.telegramBotClient.destroy()
        log.info { "Close Bot" }
        exitProcess(0)
    }
}