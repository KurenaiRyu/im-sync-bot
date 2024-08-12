package kurenai.imsyncbot.bot.qq

import kurenai.imsyncbot.snowFlake
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.Services
import top.mrxiaom.overflow.spi.FileService
import java.io.File
import java.nio.file.Path

class LocalFileService : FileService {
    override val priority: Int = 900
    override suspend fun upload(res: ExternalResource): String {
        if (useFileIfAvailable) {
            val file = when (val origin = res.origin) {
                is File -> origin
                is Path -> origin.toFile()
                else -> null
            }?.takeIf { it.exists() }
            if (file != null) {
                val path = file.absolutePath
                if (useFileBlacklist.all { !path.contains(it, true) }) {
                    return "file:///${path.removePrefix("/")}"
                }
            }
        }
        val bytes = res.inputStream().use { it.readBytes() }
        val file = runCatching {
            var tempFile: File
            do {
                tempFile = File(tempFolder, "${snowFlake.nextAlpha()}.${res.formatName}")
            } while (tempFile.exists())
            tempFile
        }.onFailure {
            log.warn("确定 ${res.formatName} 数据流的文件名时出错", it)
        }.getOrNull() ?: return ""

        return runCatching {
            file.writeBytes(bytes)
            "file:///${file.absolutePath.removePrefix("/")}"
        }.onFailure {
            log.warn("保存 ${file.name} 文件时出错", it)
        }.getOrElse { "" }
    }

    companion object {
        val log = getLogger()
        lateinit var tempFolder: File
        var useFileIfAvailable: Boolean = true
        val useFileBlacklist: MutableList<String> = mutableListOf()

        @JvmStatic
        fun register() {
            Services.register(
                FileService::class.qualifiedName!!,
                LocalFileService::class.qualifiedName!!,
                ::LocalFileService
            )
        }
    }
}