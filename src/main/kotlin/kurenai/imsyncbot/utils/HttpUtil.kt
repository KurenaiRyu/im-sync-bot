package kurenai.imsyncbot.utils

import io.ktor.http.*
import io.ktor.util.network.*
import kotlinx.coroutines.future.await
import kurenai.imsyncbot.exception.ImSyncBotRuntimeException
import kurenai.imsyncbot.getBotOrThrow
import mu.KotlinLogging
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

object HttpUtil {

    private val log = KotlinLogging.logger {}

    var PROXY: Proxy? = null
    var IMG_BASE_URL: String? = null
    private const val FILE_PART_LENGTH = 2 * 1024 * 1024L
    private val DEFAULT_TIME_OUT = Duration.ofSeconds(5)
    private val POOL = ThreadPoolExecutor(5, 10, 1L, TimeUnit.MINUTES,
        LinkedBlockingQueue(20),
        object : ThreadFactory {
            private val counter = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "Downloader#${counter.getAndIncrement()}").also {
                    it.isDaemon = true
                }
            }
        })

    suspend fun download(tgFile: moe.kurenai.tdlight.model.media.File, file: File): File {
        return download(file, tgFile.getFileUrl(getBotOrThrow().tg.token), true)
    }

    suspend fun download(file: File, url: String, enableProxy: Boolean = false): File {
        if (file.exists()) return file
        return doDownload(file, url, enableProxy)
    }

    private suspend fun doDownload(file: File, url: String, enableProxy: Boolean = false): File {
        val start = System.nanoTime()

        getRemoteFileSize(url, enableProxy).thenCompose { size ->
            if (size != null) {
                if (size < 1024 * 1024 * 100) multiPartDownload(url, file, size, enableProxy)
                else throw ImSyncBotRuntimeException("The file is too large: $size")
            } else {
                singleDownload(url, file, enableProxy)
            }
        }.handle { _, case ->
            case?.let {
                file.delete()
                throw case
            }
        }.await()

        if (file.exists() && file.length() <= 0)
            throw ImSyncBotRuntimeException("File is null: $url")
        if (!file.exists()) {
            throw ImSyncBotRuntimeException("Download file error: $url")
        }
        val timeOfMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        val speed = file.length() * 1000 / timeOfMillis
        log.info {
            "Downloaded ${file.name} ${file.length().humanReadableByteCountBin()} in ${
                String.format(
                    "%.2f",
                    timeOfMillis / 1000.0
                )
            } s (${speed.humanReadableByteCountBin()}/s)"
        }
        return file
    }

    private fun multiPartDownload(url: String, file: File, size: Long, enableProxy: Boolean = false): CompletableFuture<Void> {
        createFile(file, size)
        val partLength = if (size <= FILE_PART_LENGTH) {
            return singleDownload(url, file, enableProxy)
        } else if (size > FILE_PART_LENGTH * 20) {
            FILE_PART_LENGTH * 4
        } else {
            FILE_PART_LENGTH
        }

        val moreOne = size % partLength != 0L
        val count = (size / partLength) + if (moreOne) 1 else 0
        var offset = 0L
        val futures = ArrayList<CompletableFuture<Void>>()
        for (i in 2..count) {
            futures.add(multiPartDownload(url, file, offset, partLength, enableProxy))
            offset += partLength
        }
        if (moreOne) {
            futures.add(multiPartDownload(url, file, offset, null, enableProxy))
        }
        return CompletableFuture.allOf(*futures.toTypedArray())
    }

    private fun singleDownload(url: String, file: File, enableProxy: Boolean = false): CompletableFuture<Void> {
        return newHttpClient().sendAsync(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
            .thenAccept { response ->
                if (response.statusCode() >= 300 || response.statusCode() < 200) {
                    throw ImSyncBotRuntimeException("Single download fail: ${response.infoString()}")
                } else {
                    response.body().write(file)
                }
            }
    }

    private fun multiPartDownload(url: String, file: File, offset: Long, length: Long?, enableProxy: Boolean): CompletableFuture<Void> {
        val start = System.nanoTime()
        return newHttpClient(enableProxy)
            .sendAsync(
                HttpRequest
                    .newBuilder(URI.create(url))
                    .header(HttpHeaders.Range, "bytes=$offset-${length?.let { offset + length - 1 } ?: ""}")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream())
            .thenAccept { response ->
                val log = LogManager.getLogger()
                response.body().write(file, offset)
                response.headers().firstValue(HttpHeaders.ContentRange).ifPresent { contentRange ->
                    val range = contentRange.substringBefore("/").replace("bytes ", "")
                    val ranges = range.split("-")
                    val left = ranges[0].toLong()
                    val right = ranges[1].toLong()

                    val sizeOfMb = (right - left) / 1024.0 / 1024
                    val timeOfSeconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) / 1000.0
                    val speed = sizeOfMb / timeOfSeconds
                    log.debug(
                        "Downloaded ${file.name.substringBeforeLast(".")} part of $range ${
                            String.format(
                                "%.3f",
                                sizeOfMb
                            )
                        } MB in ${
                            String.format("%.2f", timeOfSeconds)
                        } s (${String.format("%.2f", speed)} MB/s)"
                    )
                }
            }
    }

    private fun getRemoteFileSize(url: String, enableProxy: Boolean = false): CompletableFuture<Long?> {
        return newHttpClient(enableProxy).sendAsync(
            HttpRequest
                .newBuilder(URI.create(url))
                .header(HttpHeaders.Range, "bytes=0-1")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofInputStream()
        ).handle { res, case ->
            if (case != null) {
                log.debug { "Get remote file size error: ${case.message}" }
                null
            } else if (res.statusCode() != 206) {
                log.debug { "Get remote file size fail: ${res.infoString()}" }
                null
            } else if (res.statusCode() == 404) {
                throw ImSyncBotRuntimeException("File not found: ${res.infoString()}")
            } else {
                res.headers().firstValue(HttpHeaders.ContentRange).map { it.substringAfterLast('/').toLong() }
                    .orElse(null)
            }
        }
    }

    private fun newHttpClient(enableProxy: Boolean = false): HttpClient {
        val builder = HttpClient
            .newBuilder()
            .executor(POOL)
            .connectTimeout(DEFAULT_TIME_OUT)
        if (PROXY != null && enableProxy) {
            val address = PROXY!!.address()
            builder.proxy(ProxySelector.of(InetSocketAddress(address.hostname, address.port)))
        }
        return builder.build()
    }

    private fun createFile(file: File, size: Long? = null) {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        size?.let { s ->
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(s)
                raf.close()
            }
        }
    }

    private fun InputStream.write(file: File, offset: Long = 0) {
        val b = ByteArray(DEFAULT_BUFFER_SIZE)
        var readBytes: Int
        var tmpOffset = offset
        this.use { bis ->
            RandomAccessFile(file, "rw").use { raf ->
                while (bis.read(b).also { readBytes = it } != -1) {
                    raf.seek(tmpOffset)
                    raf.write(b, 0, readBytes)
                    tmpOffset += readBytes
                }
            }
        }
    }

    private fun HttpResponse<InputStream>.infoString(): String {
        return "[${this.statusCode()}] ${this.body()?.let { "{${String(it.readAllBytes())}}" } ?: ""} (${this.uri()})"
    }
}