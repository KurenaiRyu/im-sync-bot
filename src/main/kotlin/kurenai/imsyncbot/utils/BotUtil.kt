package kurenai.imsyncbot.utils

import it.tdlight.jni.TdApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.snowFlake
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.pathString


object BotUtil {

    const val WEBP_TO_PNG_CMD_PATTERN = "dwebp %s -o %s"
    const val IMAGE_PATH = "./cache/img/"
    const val DOCUMENT_PATH = "./cache/doc/"
    const val NAME_PATTERN = "\$name"
    const val MSG_PATTERN = "\$msg"
    const val ID_PATTERN = "\$id"
    const val NEWLINE_PATTERN = "\$newline"
    const val LOG_FILE_PATH = "./logs/im-sync-bot.log"
    const val DEFAULT_BASE_URL = "https://api.telegram.org"

    private val log = getLogger()

//    @Throws(TelegramApiException::class, IOException::class)
//    suspend fun getImage(friend: Contact, fileId: String, fileUniqueId: String): Image? {
//        val file = getTgFile(fileId, fileUniqueId)
//        val image = if (file.filePath!!.lowercase().endsWith(".webp")) {
//            webp2png(file)
//        } else {
//            Path.of(file.filePath!!).takeIf { it.exists() } ?: HttpUtil.download(file, Path.of(getImagePath("$fileId.webp")))
//        }
//
//        var ret: Image? = null
//        try {
//            image.toFile().toExternalResource().use {
//                ret = friend.uploadImage(it)
//            }
//        } catch (e: IOException) {
//            log.error(e.message, e)
//        }
//        return ret
//    }
//
//    suspend fun getTgFile(fileId: String, fileUniqueId: String): TelegramFile {
//        try {
//            return GetFile(fileId).send()
//        } catch (e: TelegramApiException) {
//            log.error(e.message, e)
//        }
//        return TelegramFile(fileId, fileUniqueId)
//    }
//
//    suspend fun downloadTgFile(fileId: String, fileUniqueId: String): Path {
//        val tgFile = GetFile(fileId).send()
//        val cacheFile = tgFile.filePath?.let { Path.of(it) }
//        return if (cacheFile?.exists() == true) {
//            cacheFile
//        } else {
//            val url = tgFile.getFileUrl(getBotOrThrow().tg.token)
//            downloadDoc(tgFile.filePath?.substringAfterLast("/") ?: UUID.randomUUID().toString(), url)
//        }
//    }

    suspend fun downloadDoc(filename: String, url: String, reject: Boolean = false, overwrite: Boolean = false): Path {
        return download(Path.of(getDocumentPath(filename)), url, reject, overwrite)
    }

    suspend fun downloadImg(
        filename: String,
        url: String,
        onlyCache: Boolean = false,
        overwrite: Boolean = false
    ): Path {
        val image = Path.of(getImagePath(filename))
        return download(image, url, onlyCache, overwrite)
    }

    suspend fun downloadImg(
        url: String,
        ext: String? = null,
        onlyCache: Boolean = false,
    ): Path {
        val image = Path.of(getImagePath(snowFlake.nextAlpha()))
        val tmpPath = download(image, url, onlyCache, false)
        val path = Path.of(getImagePath(tmpPath.crc32c() + if (ext?.isNotBlank() == true) ".$ext" else ""))
        withContext(Dispatchers.IO) {
            Files.move(tmpPath, path)
        }
        return path
    }

    private suspend fun download(path: Path, url: String, onlyCache: Boolean, overwrite: Boolean): Path {
        if (!onlyCache) {
            HttpUtil.download(path, url, overwrite = overwrite)
        }
        return path
    }

    fun getImagePath(imageName: String): String {
        return IMAGE_PATH + imageName
    }

    fun getDocumentPath(docName: String): String {
        return DOCUMENT_PATH + docName
    }

    fun String.formatUsername(): String {
        return this.replace("https://", "_", true)
            .replace("http://", "", true)
            .replace(".", "_")
            .replace("/", "_")
    }

//    fun buildInlineMarkup(dataList: List<Map<String, String>>): List<List<InlineKeyboardButton>> {
//        return dataList.map { row ->
//            row.map { column ->
//                InlineKeyboardButton(column.key).apply { callbackData = column.value }
//            }
//        }
//    }

    suspend fun webp2png(file: TdApi.File): Path {
        val filename = file.remote.uniqueId
        val pngFile = Path.of(getImagePath("$filename.png"))
        if (pngFile.exists()) return pngFile
        val webpPath = file.local.path?.let(Path::of) ?: error("Webp local path cannot be null")
        pngFile.parent.createDirectories()
        withIO {
            Runtime.getRuntime()
                .exec(
                    String.format(WEBP_TO_PNG_CMD_PATTERN, webpPath.pathString, pngFile.pathString)
                        .replace("\\", "\\\\")
                )
                .onExit().await()
        }
        return pngFile
    }

    suspend fun mp42gif(width: Int, file: TdApi.File): Path {
        val filename = file.remote.uniqueId
        val gifPath = Path.of(getImagePath("$filename.gif"))
        if (gifPath.exists()) return gifPath
        val mp4Path = Path.of(file.local.path ?: error("Mp4 local file cannot be null"))
        gifPath.parent.createDirectories()
        val process = withContext(Dispatchers.IO) {
            val builder = ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i",
                mp4Path.pathString,
                "-vf",
                "scale=${minOf(width, 320)}:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
                gifPath.pathString
            )
            builder.redirectErrorStream(true)
            val process = builder.start()
            println("Execute ${builder.command().joinToString(" ")}")
            process.inputStream.bufferedReader().use { input ->
                var line = input.readLine()
                while (line != null) {
                    println(line)
                    line = input.readLine()
                }
            }
            process.onExit().await()
            println("Exit code: " + process.exitValue())
            process
        }

        if (process.exitValue() == 0 && gifPath.exists() && gifPath.fileSize() > 0) return gifPath
        else {
            throw BotException("Mp4 to Gif error")
        }
    }

}