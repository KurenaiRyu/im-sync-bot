package kurenai.imsyncbot.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.GroupConfig.qqTg
import kurenai.imsyncbot.config.GroupConfig.tgQQ
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.exception.TelegramApiException
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardButton
import moe.kurenai.tdlight.request.GetFile
import mu.KotlinLogging
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File
import java.io.IOException


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
            log.error(e) { e.message }
        }
        return ret
    }

    suspend fun getTgFile(fileId: String, fileUniqueId: String): moe.kurenai.tdlight.model.media.File {
        try {
            return GetFile(fileId).send()
        } catch (e: TelegramApiException) {
            log.error(e) { e.message }
        }
        return moe.kurenai.tdlight.model.media.File(fileId, fileUniqueId)
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

    fun formatUsername(username: String): String {
        return username.replace("https://", "_", true)
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

    suspend fun webp2png(file: moe.kurenai.tdlight.model.media.File): File {
        val pngFile = File(getImagePath("${file.fileId}.png"))
        val webpFile: File
        if (pngFile.exists()) return pngFile
        else {
            pngFile.parentFile.mkdirs()
            webpFile = File(file.filePath!!).takeIf { it.exists() } ?: File(getImagePath("${file.fileId}.webp"))
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

    suspend fun mp42gif(id: String, tgFile: moe.kurenai.tdlight.model.media.File): File? {
        val gifFile = File(getImagePath("$id.gif"))
        if (gifFile.exists()) return gifFile
        val mp4File = File(tgFile.filePath!!)
        if (!mp4File.exists()) HttpUtil.download(tgFile, mp4File)
        gifFile.parentFile.mkdirs()
        try {
            val process =
                withContext(Dispatchers.IO) {
                    Runtime.getRuntime()
                        .exec(String.format(MP4_TO_GIF_CMD_PATTERN, mp4File.path, gifFile.path).replace("\\", "\\\\"))
                }.onExit().await()
            if (process.exitValue() >= 0 || gifFile.exists()) return gifFile
            else gifFile.delete()
        } catch (e: Exception) {
            log.error(e) { e.message }
            gifFile.delete()
            throw e
        }
        return null
    }

    fun getQQGroupByTg(id: Long): Long {
        return tgQQ[id] ?: GroupConfig.defaultQQGroup
    }

    fun getTgChatByQQ(id: Long): Long {
        return qqTg[id] ?: GroupConfig.defaultTgGroup
    }

}