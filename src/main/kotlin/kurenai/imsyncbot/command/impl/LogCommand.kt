package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.BotConstant
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.Command
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File

@Component
class LogCommand : Command {

    private val log = KotlinLogging.logger {}

    override fun execute(update: Update): Boolean {
        try {
            val file = File(BotConstant.LOG_FILE_PATH)
            ContextHolder.telegramBotClient.execute(SendDocument(update.message.chatId.toString(), InputFile(file)))
        } catch (e: Exception) {
            log.error(e.message, e)
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().chatId(update.message.chatId.toString()).text("error: ${e.message}").build()
            )
        }

        return false
    }

    override fun match(text: String): Boolean {
        return text.startsWith("/log")
    }

    override fun getHelp(): String {
        return "/log 获取日志文件"
    }
}