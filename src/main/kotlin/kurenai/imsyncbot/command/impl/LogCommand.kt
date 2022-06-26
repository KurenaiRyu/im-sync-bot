package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.BotConstant
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.SendDocument
import mu.KotlinLogging
import java.io.File

class LogCommand : AbstractTelegramCommand() {

    private val log = KotlinLogging.logger {}
    override val help: String = "获取日志文件"
    override val command: String = "log"

    override fun execute(update: Update, message: Message): String? {
        return try {
            val msg = update.message!!
            val file = File(BotConstant.LOG_FILE_PATH)
            SendDocument(msg.chatId, InputFile(file)).send()
            null
        } catch (e: Exception) {
            log.error(e.message, e)
            "error: ${e.message}"
        }
    }
}