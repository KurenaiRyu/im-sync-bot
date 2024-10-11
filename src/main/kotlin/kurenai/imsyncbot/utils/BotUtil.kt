package kurenai.imsyncbot.utils

import it.tdlight.jni.TdApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.domain.by
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.imSyncBot
import kurenai.imsyncbot.snowFlake
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.source
import org.babyfish.jimmer.kt.new
import top.mrxiaom.overflow.Overflow
import top.mrxiaom.overflow.contact.RemoteBot
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.*


object BotUtil {

    const val IMAGE_PATH = "./cache/img/"
    const val DOCUMENT_PATH = "./cache/doc/"
    const val NAME_PATTERN = "\$name"
    const val MSG_PATTERN = "\$msg"
    const val ID_PATTERN = "\$id"
    const val NEWLINE_PATTERN = "\$newline"

    private val log = getLogger()

    suspend fun downloadDoc(filename: String, url: String, reject: Boolean = false, overwrite: Boolean = false): Path {
        return download(Path.of(getDocumentPath(filename)), url, reject, overwrite)
    }

    suspend fun downloadImg(
        filename: String,
        url: String,
        onlyCache: Boolean = false,
        overwrite: Boolean = false
    ): Path {
        val path = Path.of(getImagePath(filename))
        return download(path, url, onlyCache, overwrite)
    }

    suspend fun downloadImg(
        url: String,
        ext: String = "png",
        onlyCache: Boolean = false,
    ): Path {
        val image = Path.of(getImagePath(snowFlake.nextAlpha()))
        val tmpPath = download(image, url, onlyCache, false)
        val type = ImageUtil.determineImageType(tmpPath)
        val e = if (type != ImageUtil.ImageType.UNKNOWN) {
            type.ext
        } else ext

        val path = Path.of(getImagePath(tmpPath.crc32c() + if (e.isNotBlank()) ".$e" else ""))
        if (path.exists()) tmpPath.deleteExisting()
        else {
            withContext(Dispatchers.IO) {
                Files.move(tmpPath, path)
            }
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
//        val tmpFile = Path.of(getImagePath("$filename-tmp.png"))

        val toPngProcess = runCommandAwait(
            "dwebp",
            webpPath.pathString,
            "-o",
            pngFile.pathString
        )
        if (toPngProcess.exitValue() != 0 || !pngFile.exists() || pngFile.fileSize() == 0L) throw BotException("Webp to png fail")

//        val resizeProcess = runCommandAwait(
//            "ffmpeg",
//            "-y",
//            "-i",
//            tmpFile.pathString,
//            "-vf",
//            "scale=320:-1",
//            pngFile.pathString
//        )
//        if (resizeProcess.exitValue() != 0  || !pngFile.exists() || pngFile.fileSize() == 0L) throw BotException("Png resize fail")

        return pngFile
    }

    suspend fun mp42gif(width: Int, file: TdApi.File): Path {
        val filename = file.remote.uniqueId
        val gifPath = Path.of(getImagePath("$filename.gif"))
        if (gifPath.exists()) return gifPath
        val mp4Path = Path.of(file.local.path ?: error("Mp4 local file cannot be null"))
        gifPath.parent.createDirectories()
        val process = runCommandAwait(
            "ffmpeg",
            "-y",
            "-i",
            mp4Path.pathString,
            "-vf",
            "scale=${minOf(width, 320)}:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
            gifPath.pathString
        )

        if (process.exitValue() == 0 && gifPath.exists() && gifPath.fileSize() > 0) return gifPath
        else {
            throw BotException("Mp4 to Gif fail")
        }
    }

    ///////////////////////////  message  ///////////////////////////

    fun MessageEvent.toEntity(handled: Boolean = false): QQMessage {
        return this.message.toEntity(handled)
    }

    fun MessageChain.toEntity(handled: Boolean = false): QQMessage {
        val source = this.source
        val jsonTxt = Overflow.serializeMessage(imSyncBot.qq.qqBot as? RemoteBot, this)
        return new(QQMessage::class).by {
            messageId = source.ids[0]
            botId = source.botId
            targetId = source.targetId
            fromId = source.fromId
            type = source.kind
            json = jsonTxt
            this.handled = handled
            time = source.localDateTime()
        }
    }

    suspend fun QQMessage.toSource(): MessageSource {
        return Overflow.deserializeMessage(imSyncBot.qq.qqBot, this.json).source
//        val entity = this
//        return MessageSourceBuilder().apply {
//            id(entity.messageId)
//            internalId(entity.messageId)
//            fromId = entity.fromId
//            targetId = entity.targetId
//            time = entity.time.atZone(ZoneOffset.ofHours(8)).toEpochSecond().toInt()
//
//        }.build(entity.botId, entity.type)
    }

    fun MessageSource.localDateTime(): LocalDateTime =
        LocalDateTime.ofEpochSecond(this.time.toLong(), 0, ZoneOffset.ofHours(8))

}