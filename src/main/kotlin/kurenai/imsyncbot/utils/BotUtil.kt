package kurenai.imsyncbot.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.asCache
import it.tdlight.jni.TdApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.domain.by
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.imSyncBot
import kurenai.imsyncbot.service.HttpFileService
import kurenai.imsyncbot.snowFlake
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.MessageSourceBuilder
import net.mamoe.mirai.message.data.source
import okio.Path
import okio.Path.Companion.toPath
import org.babyfish.jimmer.kt.new
import top.mrxiaom.overflow.Overflow
import top.mrxiaom.overflow.contact.RemoteBot
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit


object BotUtil {

    const val IMAGE_PATH = "./cache/img/"
    const val DOCUMENT_PATH = "./cache/doc/"
    const val NAME_PATTERN = "\$name"
    const val MSG_PATTERN = "\$msg"
    const val ID_PATTERN = "\$id"
    const val NEWLINE_PATTERN = "\$newline"

    private val log = getLogger()
    private val mutex = Mutex()
    private val fileCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .asCache<String, Path>()

    suspend fun downloadDoc(filename: String, url: String, reject: Boolean = false, overwrite: Boolean = false): Path {
        return download(getDocumentPath(filename).toPath(true), url, reject, overwrite)
    }

    suspend fun downloadImg(
        filename: String,
        url: String,
        onlyCache: Boolean = false,
        overwrite: Boolean = false
    ): Path = mutex.withLock {
        if (url.contains("://${HttpFileService.host}:${HttpFileService.port}", true))
            return HttpFileService.retrievePath(url)

        fileCache.get(url) {
            val path = getImagePath(filename).toPath(true)
            download(path, url, onlyCache, overwrite)
        }
    }

    suspend fun downloadImg(
        url: String,
        ext: String = "png",
        onlyCache: Boolean = false,
    ): Path = mutex.withLock {
        if (url.contains("://${HttpFileService.host}:${HttpFileService.port}", true))
            return HttpFileService.retrievePath(url)

        fileCache.get(url) {
            val image = getImagePath(snowFlake.nextAlpha()).toPath(true)
            val tmpPath = download(image, url, onlyCache, false)
            val type = ImageUtil.determineImageType(tmpPath)
            val ext = if (type != ImageUtil.ImageType.UNKNOWN) type.ext else ext
            val path = getImagePath(tmpPath.crc32c() + if (ext.isNotBlank()) ".$ext" else "").toPath(true)
            if (fs.exists(path)) fs.delete(tmpPath)
            else {
                withContext(Dispatchers.VT) {
                    fs.atomicMove(tmpPath, path)
                }
            }
            path
        }
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
        val pngFile = getImagePath("$filename.png").toPath(true)
        if (fs.exists(pngFile)) return pngFile
        val webpPath = file.local.path?.toPath(true) ?: error("Webp local path cannot be null")
        pngFile.parent?.run { fs.createDirectories(this) }
//        val tmpFile = Path.of(getImagePath("$filename-tmp.png"))

        val toPngProcess = runCommandAwait(
            "dwebp",
            webpPath.toString(),
            "-o",
            pngFile.toString()
        )
        if (toPngProcess.exitValue() != 0 || !fs.exists(pngFile) || fs.metadata(pngFile).size == 0L) throw BotException("Webp to png fail")

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

    suspend fun toWebp(src: Path): Path {
        val webpPath = getImagePath(src.name.substringBeforeLast(".") + ".webp").toPath(true)
        val toPngProcess = runCommandAwait(
            "cwebp",
            src.toString(),
            "-o",
            webpPath.toString()
        )
        if (toPngProcess.exitValue() != 0 || !fs.exists(webpPath) || fs.metadata(webpPath).size == 0L) throw BotException("Webp to png fail")
        return webpPath
    }

    suspend fun mp42gif(width: Int, file: TdApi.File): Path {
        val filename = file.remote.uniqueId
        val gifPath = getImagePath("$filename.gif").toPath(true)
        if (fs.exists(gifPath)) return gifPath
        val mp4Path = file.local.path ?: error("Mp4 local file cannot be null").toPath(true)
        gifPath.parent?.run {fs.createDirectories(this) }
        val process = runCommandAwait(
            "ffmpeg",
            "-y",
            "-i",
            mp4Path.toString(),
            "-vf",
            "scale=${minOf(width, 320)}:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
            gifPath.toString()
        )

        if (process.exitValue() == 0 && fs.exists(gifPath) && (fs.metadata(gifPath).size ?: 0) > 0L) return gifPath
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

    suspend fun QQMessage.toMessageChain(): MessageChain {
        val entity = this
        return this.toSource().plus(Overflow.deserializeMessageFromJson(imSyncBot.qq.qqBot, entity.json)!!)
    }

    fun QQMessage.toSource(): MessageSource {
        val entity = this

        return MessageSourceBuilder().apply {
            id(entity.messageId)
            internalId(entity.messageId)
            fromId = entity.fromId
            targetId = entity.targetId
            time = entity.time.atZone(ZoneOffset.ofHours(8)).toEpochSecond().toInt()

        }.build(entity.botId, entity.type)
    }

    fun MessageSource.localDateTime(): LocalDateTime =
        LocalDateTime.ofEpochSecond(this.time.toLong(), 0, ZoneOffset.ofHours(8))

}