package kurenai.imsyncbot.utils

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.exception.BotException
import okio.Path
import okio.Path.Companion.toPath
import java.util.concurrent.TimeUnit

object HttpUtil {

    private val log = getLogger()

    private val client = HttpClient(OkHttp) {
        defaultRequest {
            url.protocol = URLProtocol.HTTPS
        }
    }

    suspend fun download(path: Path, url: String, enableProxy: Boolean = false, overwrite: Boolean): Path {
        return if (!overwrite && fs.exists(path)) path
        else if (!url.startsWith("http")) url.toPath(true)
        else doDownload(path, url, enableProxy)
    }

    private suspend fun doDownload(path: Path, url: String, enableProxy: Boolean = false): Path {
        val start = System.nanoTime()

        val size = getRemoteFileSize(url, enableProxy)
        if (size < 1024 * 1024 * 100) {
            withContext(Dispatchers.VT) {
                path.parent?.run {fs.createDirectories(this) }
                val channel = client.get(url).bodyAsChannel()
                fs.write(path, false) {
                    while (!channel.isClosedForRead) {
                        write(channel.readByteArray(channel.availableForRead.coerceAtMost(DEFAULT_BUFFER_SIZE)))
                    }
                }
            }
        } else {
            throw BotException("The file is too large: $size")
        }

        val exists = fs.exists(path)
        val pathSize = if (exists) fs.metadataOrNull(path)?.size?:0L else 0
        val timeOfMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        val speed = pathSize * 1000 / timeOfMillis
        if (exists && pathSize <= 0)
            throw BotException("File is null: $url")
        if (!exists) {
            throw BotException("Download file error: $url")
        }
        log.info(
            "Downloaded ${path.name} ${pathSize.humanReadableByteCountBin()} in ${
                String.format(
                    "%.2f",
                    timeOfMillis / 1000.0
                )
            } s (${speed.humanReadableByteCountBin()}/s)"
        )
        return path
    }

    suspend fun getRemoteFileSize(url: String, enableProxy: Boolean = false): Long {
        val response = client.head(url)
        return when (response.status) {
            HttpStatusCode.PartialContent -> {
                log.debug("Get remote file size fail")
                0
            }

            HttpStatusCode.NotFound -> {
                throw BotException("File not found")
            }

            else -> {
                response.headers[HttpHeaders.ContentLength]?.toLong() ?: 0L
            }
        }
    }
}