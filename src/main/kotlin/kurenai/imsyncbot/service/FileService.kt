package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.InputFileLocal
import kotlinx.coroutines.flow.channelFlow
import kurenai.imsyncbot.domain.FileCache
import kurenai.imsyncbot.fileCacheRepository
import kurenai.imsyncbot.utils.*
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.MiraiInternalApi
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull

/**
 * @author Kurenai
 * @since 2023/6/6 21:20
 */

object FileService {

    @OptIn(MiraiInternalApi::class)
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
        val path = BotUtil.downloadImg(image.queryUrl(), ext)
        fileCacheRepository.findById(path.crc32c()).getOrNull()?.let {
            TdApi.InputFileRemote(it.fileId)
        } ?: run {
            InputFileLocal(path.pathString)
        }
    }

    @OptIn(MiraiInternalApi::class)
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
            send(InputFileLocal(BotUtil.downloadImg(it.queryUrl()).pathString))
        }
    }

    @OptIn(MiraiInternalApi::class)
    suspend fun cacheEmoji(images: List<Image>, messages: Array<TdApi.Message>) {
        withIO {
            messages.mapIndexedNotNull { index, message ->
                val image = images[index]
                if (image.isEmoji.not()) return@mapIndexedNotNull null
                message.content.file()?.let {
                    FileCache().apply {
                        this.id = image.md5.toHex()
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
    suspend fun cacheEmoji(image: Image, message: TdApi.Message) {
        if (image.isEmoji.not()) return
        cacheEmoji(image.md5.toHex(), message, image.imageType.formatName)
    }

    private suspend fun cacheEmoji(md5Hex: String, message: TdApi.Message, fileType: String? = null) {
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

}