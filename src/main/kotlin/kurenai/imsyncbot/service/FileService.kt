package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.InputFile
import it.tdlight.jni.TdApi.InputFileLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.domain.FileCache
import kurenai.imsyncbot.fileCacheRepository
import kurenai.imsyncbot.utils.*
import kurenai.imsyncbot.utils.BotUtil.getImagePath
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.MiraiInternalApi
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.jvm.optionals.getOrNull

/**
 * @author Kurenai
 * @since 2023/6/6 21:20
 */

object FileService {

    suspend fun download(image: Image, ext: String = "png") = withIO {
//        fileCacheRepository.findById(image.md5.toHex()).getOrNull()?.let {
//            InputFileRemote(it.fileId)
//        } ?: run {
//            InputFileLocal(
//                BotUtil.downloadImg(
//                    "${image.imageId.substring(1..36).replace("-", "")}.${image.imageType.formatName}",
//                    image.queryUrl()
//                ).pathString
//            )
//        }
        download(image.queryUrl())
    }

    suspend fun download(images: Iterable<Image>) = channelFlow {
//        val imgMap = images.associateBy { it.md5.toHex() }.toMutableMap()
//        val caches = withIO { fileCacheRepository.findAllById(imgMap.keys) }
//        caches.forEach {
//            send(InputFileRemote(it.fileId))
//            imgMap.remove(it.id)
//        }
//
//        imgMap.entries.takeIf { it.isNotEmpty() }?.forEach { (_, img) ->
//            val filename = "${img.imageId.substring(1..36).replace("-", "")}.${img.imageType.formatName}"
//            send(InputFileLocal(BotUtil.downloadImg(filename, img.queryUrl()).pathString))
//        }

        images.forEach {
            send(download(it.queryUrl()))
        }
    }

    suspend fun download(url: String, ext: String = "png") = withIO {
        val localFile = !url.startsWith("http")

        val tmpPath =
            if (localFile) URI.create(url).toPath()
            else BotUtil.downloadImg(url, ext)
        val crc32c = tmpPath.crc32c()

        fileCacheRepository.findById(crc32c).getOrNull()?.let {
            return@withIO ImageInfo(crc32c, url, TdApi.InputFileRemote(it.fileId), ImageUtil.ImageType.valueOf(it.fileType))
        }

        // return if image has extension
        val tmpExt = tmpPath.name.substringAfterLast(".")
        if (tmpExt.isNotEmpty()) {
            val type = ImageUtil.ImageType.values().find { it.ext == tmpExt }?: ImageUtil.ImageType.UNKNOWN
            return@withIO ImageInfo(crc32c, url, InputFileLocal(tmpPath.pathString), type)
        }

        val type = ImageUtil.determineImageType(tmpPath)
        val extension =
            if (type != ImageUtil.ImageType.UNKNOWN) type.ext
            else tmpExt.ifBlank { "png" }


        val path = Path.of(getImagePath("$crc32c.$extension"))
        if (!path.exists()) {
            withContext(Dispatchers.IO) {
                Files.move(tmpPath, path)
            }
        }
        ImageInfo(crc32c, url, InputFileLocal(path.pathString), type)
    }

    @OptIn(MiraiInternalApi::class)
    suspend fun cacheImage(images: List<Image>, messages: Array<TdApi.Message>) {
        withIO {
            messages.mapIndexedNotNull { index, message ->
                val image = images[index]
                message.content.file()?.let {
                    FileCache().apply {
                        this.id = image.imageId
                        this.fileId = it.remote.id
                        this.fileType = image.imageType.formatName
                    }
                }
            }.filter {
                it.fileId?.isNotBlank() == true && it.id?.isNotBlank() == true
            }.let(fileCacheRepository::saveAll)
        }
    }

    @OptIn(MiraiInternalApi::class)
    suspend fun cacheImage(image: Image, message: TdApi.Message) {
        if (image.isEmoji.not()) return
        cacheImage(image.md5.toHex(), message, image.imageType.formatName)
    }

    private suspend fun cacheImage(md5Hex: String, message: TdApi.Message, fileType: String? = null) {
        withIO {
            message.content.file()?.remote?.id?.takeIf { it.isNotBlank() }?.let { fileId ->
                val exist = fileCacheRepository.findById(md5Hex).orElse(null)
                if (exist != null && (exist.fileId != fileId || exist.fileType != fileType)) {
                    exist.fileId = fileId
                    exist.fileType = fileType
                    fileCacheRepository.save(exist)
                }
                if (exist == null) {
                    fileCacheRepository.save(
                        FileCache().apply {
                            this.id = md5Hex
                            this.fileId = fileId
                            this.fileType = fileType
                        })
                }
            }
        }
    }

    data class ImageInfo(
        val id: String,
        val url: String,
        val inputFile: InputFile,
        val type: ImageUtil.ImageType
    )
}