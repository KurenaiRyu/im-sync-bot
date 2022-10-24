package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.BotUtil
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.SendDocument
import mu.KotlinLogging
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LogCommand : AbstractTelegramCommand() {

    private val log = KotlinLogging.logger {}
    override val help: String = "获取日志文件"
    override val command: String = "log"

    override suspend fun execute(update: Update, message: Message): String? {
        return try {
            val msg = update.message!!
            val file = File(BotUtil.LOG_FILE_PATH)
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
            val zipFile = File(BotUtil.DOCUMENT_PATH, "im-sync-bot-log-$now.zip")
            ZipOutputStream(zipFile.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("im-sync-bot-$now.log"))
                out.write(file.readBytes())
            }
            SendDocument(msg.chatId, InputFile(zipFile)).send()
            null
        } catch (e: Exception) {
            log.error(e.message, e)
            "error: ${e.message}"
        }
    }
}