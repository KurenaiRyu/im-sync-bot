package kurenai.imsyncbot.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.exception.TelegramApiException
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardButton
import moe.kurenai.tdlight.request.GetFile
import moe.kurenai.tdlight.util.getLogger
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File
import java.io.IOException
import java.util.*
import moe.kurenai.tdlight.model.media.File as TelegramFile


object BotUtil {

    const val WEBP_TO_PNG_CMD_PATTERN = "dwebp %s -o %s"
    const val MP4_TO_GIF_CMD_PATTERN = "ffmpeg -i %s -vf \"scale=%s:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse\" %s"
    const val IMAGE_PATH = "./cache/img/"
    const val DOCUMENT_PATH = "./cache/doc/"
    const val NAME_PATTERN = "\$name"
    const val MSG_PATTERN = "\$msg"
    const val ID_PATTERN = "\$id"
    const val NEWLINE_PATTERN = "\$newline"
    const val LOG_FILE_PATH = "./logs/im-sync-bot.log"
    const val DEFAULT_BASE_URL = "https://api.telegram.org"

    private val log = getLogger()

    @Throws(TelegramApiException::class, IOException::class)
    suspend fun getImage(friend: Contact, fileId: String, fileUniqueId: String): Image? {
        val file = getTgFile(fileId, fileUniqueId)
        val image = if (file.filePath!!.lowercase().endsWith(".webp")) {
            webp2png(file)
        } else {
            File(file.filePath!!).takeIf { it.exists() } ?: HttpUtil.download(file, File(getImagePath("$fileId.webp")))
        }

        var ret: Image? = null
        try {
            image.toExternalResource().use {
                ret = friend.uploadImage(it)
            }
        } catch (e: IOException) {
            log.error(e.message, e)
        }
        return ret
    }

    suspend fun getTgFile(fileId: String, fileUniqueId: String): TelegramFile {
        try {
            return GetFile(fileId).send()
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
        return TelegramFile(fileId, fileUniqueId)
    }

    suspend fun downloadTgFile(fileId: String, fileUniqueId: String): File {
        val tgFile = GetFile(fileId).send()
        val cacheFile = tgFile.filePath?.let { File(it) }
        return if (cacheFile?.exists() == true) {
            cacheFile
        } else {
            val url = tgFile.getFileUrl(getBotOrThrow().tg.token)
            downloadDoc(tgFile.filePath?.substringAfterLast("/") ?: UUID.randomUUID().toString(), url)
        }
    }

    suspend fun downloadDoc(filename: String, url: String, reject: Boolean = false): File {
        return download(File(getDocumentPath(filename)), url, reject)
    }

    suspend fun downloadImg(filename: String, url: String, reject: Boolean = false): File {
        val image = File(getImagePath(filename))
        return download(image, url, reject).also {
            CacheService.cacheImg(image)
        }
    }

    private suspend fun download(file: File, url: String, reject: Boolean): File {
        if (!reject) {
            HttpUtil.download(file, url)
        }
        return file
    }

    fun getImagePath(imageName: String): String {
        return IMAGE_PATH + imageName
    }

    fun getDocumentPath(docName: String): String {
        return DOCUMENT_PATH + docName
    }

    fun getSuffix(path: String?): String {
        return path?.substring(path.lastIndexOf('.').plus(1)) ?: ""
    }

    fun String.formatUsername(): String {
        return this.replace("https://", "_", true)
            .replace("http://", "", true)
            .replace(".", "_")
            .replace("/", "_")
    }

    fun buildInlineMarkup(dataList: List<Map<String, String>>): List<List<InlineKeyboardButton>> {
        return dataList.map { row ->
            row.map { column ->
                InlineKeyboardButton(column.key).apply { callbackData = column.value }
            }
        }
    }

    suspend fun webp2png(file: TelegramFile): File {
        val filename = file.fileUniqueId
        val pngFile = File(getImagePath("$filename.png"))
        val webpFile: File
        if (pngFile.exists()) return pngFile
        else {
            pngFile.parentFile.mkdirs()
            webpFile = File(file.filePath!!).takeIf { it.exists() } ?: File(getImagePath("$filename.webp"))
            if (!webpFile.exists()) {
                HttpUtil.download(file, webpFile)
            }
        }
        withContext(Dispatchers.IO) {
            Runtime.getRuntime()
                .exec(String.format(WEBP_TO_PNG_CMD_PATTERN, webpFile.path, pngFile.path).replace("\\", "\\\\"))
        }.onExit().await()
//        val webp = ImageIO.read(webpFile)
//        ImageIO.write(webp, "png", pngFile)
        return pngFile
    }

    suspend fun mp42gif(width: Int, tgFile: TelegramFile): File? {
        val filename = tgFile.fileUniqueId
        val gifFile = File(getImagePath("$filename.gif"))
        if (gifFile.exists()) return gifFile
        var mp4File = File(tgFile.filePath!!)
        if (!mp4File.exists()) {
            mp4File = File(getImagePath("$filename.mp4"))
            HttpUtil.download(tgFile, mp4File)
        }
        gifFile.parentFile.mkdirs()
        try {
            val process =
                withContext(Dispatchers.IO) {
                    Runtime.getRuntime()
                        .exec(String.format(MP4_TO_GIF_CMD_PATTERN, mp4File.path, minOf(width, 320), gifFile.path).replace("\\", "\\\\"))
                }.onExit().await()
            if (process.exitValue() >= 0 || gifFile.exists()) return gifFile
            else gifFile.delete()
        } catch (e: Exception) {
            log.error(e.message, e)
            gifFile.delete()
            throw e
        }
        return null
    }

}