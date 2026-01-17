package kurenai.imsyncbot.service

import io.ktor.http.*
import io.vertx.ext.web.RoutingContext
import kurenai.imsyncbot.configProperties
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.Services
import top.mrxiaom.overflow.spi.FileService
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.pathString

class HttpFileService : FileService {
    override val priority: Int = 900
    override suspend fun upload(res: ExternalResource): String {
        val origin = res.origin ?: throw IllegalArgumentException("No origin in resource")
        val path = when (origin) {
            is File -> origin.toPath()
            is Path -> origin
            is String -> Paths.get(origin)
            else -> throw IllegalArgumentException("not supported resource type ${origin::class}")
        }

        val relativize = basePath.relativize(path.toAbsolutePath()).pathString.encodeURLPath()

        return "http://$host:$port/file/$relativize"
    }

    companion object {
        val log = getLogger()
        var basePath: Path = Paths.get("./").toAbsolutePath()
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

        fun retrieveFile(ctx: RoutingContext) {
            val filePath = basePath.resolve(ctx.request().path().substringAfter("/file/"))
            if (!filePath.exists()) {
                ctx.response().setStatusCode(404).end("File not found ${filePath.toAbsolutePath().pathString}")
            } else {
                ctx.response().sendFile(RandomAccessFile(filePath.toFile(), "r"))
            }
        }
    }
}