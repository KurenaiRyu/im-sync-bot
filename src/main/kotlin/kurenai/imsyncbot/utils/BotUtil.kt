package kurenai.imsyncbot.utils

import kurenai.imsyncbot.ContextHolder
import mu.KotlinLogging
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException

object BotUtil {

    const val WEBP_TO_PNG_CMD_PATTERN = "dwebp %s -o %s"
    const val MP4_TO_GIF_CMD_PATTERN = "ffmpeg -i %s %s"
    const val IMAGE_PATH = "./cache/img/"
    const val DOCUMENT_PATH = "./cache/doc/"
    const val NAME_PATTERN = "\$name"
    const val MSG_PATTERN = "\$msg"
    const val ID_PATTERN = "\$id"
    const val NEWLINE_PATTERN = "\$newline"

    private val log = KotlinLogging.logger {}

    fun getImagePath(imageName: String): String {
        return IMAGE_PATH + imageName
    }

    fun getDocumentPath(docName: String): String {
        return DOCUMENT_PATH + docName
    }

    fun getSuffix(path: String?): String {
        return path?.substring(path.lastIndexOf('.').plus(1)) ?: ""
    }

    fun formatUsername(username: String): String {
        return username.replace("https://", "", true)
            .replace("http://", "", true)
            .replace(".", " .")
            .replace("/", "-")
    }

    fun webp2png(id: String, webpFile: File): File? {
        val pngFile = File(getImagePath("$id.png"))
        if (pngFile.exists()) return pngFile
        pngFile.parentFile.mkdirs()
        try {
            val future =
                Runtime.getRuntime().exec(String.format(WEBP_TO_PNG_CMD_PATTERN, webpFile.path, pngFile.path).replace("\\", "\\\\")).onExit()
            if (future.get().exitValue() >= 0 || pngFile.exists()) return pngFile
        } catch (e: IOException) {
            log.error(e) { e.message }
        } catch (e: ExecutionException) {
            log.error(e) { e.message }
        } catch (e: InterruptedException) {
            log.error(e) { e.message }
        }
        return null
    }

    fun mp42gif(id: String, tgFile: org.telegram.telegrambots.meta.api.objects.File): File? {
        val gifFile = File(getImagePath("$id.gif"))
        if (gifFile.exists()) return gifFile
        var mp4File = File(tgFile.filePath)
        if (!mp4File.exists()) mp4File = ContextHolder.telegramBotClient.downloadFile(tgFile)
        gifFile.parentFile.mkdirs()
        try {
            val future =
                Runtime.getRuntime().exec(String.format(MP4_TO_GIF_CMD_PATTERN, mp4File.path, gifFile.path).replace("\\", "\\\\")).onExit()
            if (future.get().exitValue() >= 0 || gifFile.exists()) return gifFile
        } catch (e: IOException) {
            log.error(e) { e.message }
        } catch (e: ExecutionException) {
            log.error(e) { e.message }
        } catch (e: InterruptedException) {
            log.error(e) { e.message }
        }
        return null
    }

    fun getQQGroupByTg(id: Long): Long {
        return ContextHolder.tgQQBinding[id] ?: ContextHolder.defaultQQGroup
    }

    fun getTgChatByQQ(id: Long): Long {
        return ContextHolder.qqTgBinding[id] ?: ContextHolder.defaultTgGroup
    }

}