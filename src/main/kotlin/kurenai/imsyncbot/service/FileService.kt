package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.InputFileLocal
import it.tdlight.jni.TdApi.InputFileRemote
import kotlinx.coroutines.flow.channelFlow
import kurenai.imsyncbot.domain.FileCache
import kurenai.imsyncbot.fileCacheRepository
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.TelegramUtil.file
import kurenai.imsyncbot.utils.toHex
import kurenai.imsyncbot.utils.withIO
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.ImageType
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull

/**
 * @author Kurenai
 * @since 2023/6/6 21:20
 */

object FileService {

    suspend fun download(image: Image) = withIO {
        fileCacheRepository.findById(image.md5.toHex()).getOrNull()?.let {
            InputFileRemote(it.fileId)
        } ?: run {
            InputFileLocal(BotUtil.downloadImg(image.imageId, image.queryUrl()).pathString)
        }
    }

    suspend fun download(images: List<Image>) = channelFlow {
        val imgMap = images.associateBy { it.md5.toHex() }.toMutableMap()
        val caches = withIO { fileCacheRepository.findAllById(imgMap.keys) }
        caches.forEach {
            send(InputFileRemote(it.fileId))
            imgMap.remove(it.id)
        }

        imgMap.entries.takeIf { it.isNotEmpty() }?.forEach { (key, img) ->
            val filename = if (img.imageType == ImageType.UNKNOWN) "$key.jpg" else img.imageId
            send(InputFileLocal(BotUtil.downloadImg(filename, img.queryUrl()).pathString))
        }
    }

    suspend fun cache(images: List<Image>, messages: List<TdApi.Message>) {
        withIO {
            messages.mapIndexedNotNull { index, message ->
                message.content.file()?.let {
                    FileCache().apply {
                        this.id = images[index].md5.toHex()
                        this.fileId = it.remote.id
                    }
                }
            }.let(fileCacheRepository::saveAll)
        }
    }

    suspend fun cache(image: Image, message: TdApi.Message) {
        cache(image.md5.toHex(), message)
    }

    suspend fun cache(md5Hex: String, message: TdApi.Message) {
        withIO {
            message.content.file()?.remote?.id?.takeIf { it.isNotBlank() }?.let {
                fileCacheRepository.save(
                    FileCache().apply {
                        this.id = md5Hex
                        this.fileId = it
                    })
            }
        }
    }

}