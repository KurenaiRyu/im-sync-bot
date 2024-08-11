package kurenai.imsyncbot.utils

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.exception.BotException
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

object HttpUtil {

    private val log = getLogger()

    private val client = HttpClient()

//    suspend fun download(tgFile: TelegramFile, path: Path): Path {
//        return download(path, tgFile.getFileUrl(getBotOrThrow().tg.token), true, false)
//    }

    suspend fun download(path: Path, url: String, enableProxy: Boolean = false, overwrite: Boolean): Path {
        return if (!overwrite && path.exists()) path
        else if (!url.startsWith("http")) URI.create(url).toPath()
        else doDownload(path, url, enableProxy)
    }

    private suspend fun doDownload(path: Path, url: String, enableProxy: Boolean = false): Path {
        val start = System.nanoTime()

        val size = getRemoteFileSize(url, enableProxy)
        if (size < 1024 * 1024 * 100) {
            withContext(Dispatchers.IO) {
                path.parent.createDirectories()
                client.get(url).body<InputStream>().buffered().use { input ->
                    path.outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).buffered().use {
                        input.copyTo(it)
                    }
                }
            }
        } else {
            throw BotException("The file is too large: $size")
        }

        val timeOfMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        val speed = path.fileSize() * 1000 / timeOfMillis
        if (path.exists() && path.fileSize() <= 0)
            throw BotException("File is null: $url")
        if (!path.exists()) {
            throw BotException("Download file error: $url")
        }
        log.info(
            "Downloaded ${path.fileName} ${path.fileSize().humanReadableByteCountBin()} in ${
                String.format(
                    "%.2f",
                    timeOfMillis / 1000.0
                )
            } s (${speed.humanReadableByteCountBin()}/s)"
        )
        return path
    }

    private suspend fun getRemoteFileSize(url: String, enableProxy: Boolean = false): Long {
        val response = client.get(url) {
            headers {
                append(HttpHeaders.Range, "bytes=0-1")
            }
        }
        return when (response.status) {
            HttpStatusCode.PartialContent -> {
                log.debug("Get remote file size fail")
                0
            }

            HttpStatusCode.NotFound -> {
                throw BotException("File not found")
            }

            else -> {
                response.headers[HttpHeaders.ContentRange]?.substringAfterLast('/')?.toLong() ?: 0
            }
        }
    }
}