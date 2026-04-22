package kurenai.imsyncbot.service

import io.ktor.http.*
import io.vertx.ext.web.RoutingContext
import kurenai.imsyncbot.configProperties
import kurenai.imsyncbot.utils.fs
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.Services
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import top.mrxiaom.overflow.spi.FileService
import java.io.File
import java.io.RandomAccessFile

class HttpFileService : FileService {
    override val priority: Int = 900
    override suspend fun upload(res: ExternalResource): String {
        val origin = res.origin ?: throw IllegalArgumentException("No origin in resource")
        val path = when (origin) {
            is File -> origin.toOkioPath(true)
            is java.nio.file.Path -> origin.toOkioPath(true)
            is String -> origin.toPath(true)
            else -> throw IllegalArgumentException("not supported resource type ${origin::class}")
        }

        val relativize = path.toString()

        return "http://$host:$port/file/$relativize"
    }

    companion object {
        val log = getLogger()
        var host = configProperties.bot.fileServer.host
        var port = configProperties.bot.fileServer.port
        var useFileIfAvailable: Boolean = true
        val useFileBlacklist: MutableList<String> = mutableListOf()

        @JvmStatic
        fun register() {
            Services.register(
                FileService::class.qualifiedName!!,
                HttpFileService::class.qualifiedName!!,
                ::HttpFileService
            )
        }

        fun retrieveFileAndResponse(ctx: RoutingContext) = ctx.apply {
            val filePath =
                request()?.path()?.substringAfter("file/")?.decodeURLPart()?.toPath(true) ?: error("No file path")
            if (!fs.exists(filePath)) {
                response().setStatusCode(404).end("File not found $filePath")
            } else {
                response().sendFile(RandomAccessFile(filePath.toFile(), "r"))
            }
        }

        fun retrievePath(url: String): Path {
            return url.substringAfter("file/").toPath(true)
        }
    }
}