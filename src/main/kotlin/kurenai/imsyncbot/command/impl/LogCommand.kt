package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.BotConstant
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractCommand
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File

@Component
class LogCommand : AbstractCommand() {

    private val log = KotlinLogging.logger {}
    override val help: String = "获取日志文件"
    override val command: String = "log"

    override fun execute(update: Update, message: Message): String? {
        return try {
            val file = File(BotConstant.LOG_FILE_PATH)
            ContextHolder.telegramBotClient.send(SendDocument(update.message.chatId.toString(), InputFile(file)))
            null
        } catch (e: Exception) {
            log.error(e.message, e)
            "error: ${e.message}"
        }
    }
}