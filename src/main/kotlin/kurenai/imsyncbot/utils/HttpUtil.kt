package kurenai.imsyncbot.utils

import io.ktor.util.network.*
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.exception.ImSyncBotRuntimeException
import mu.KotlinLogging
import org.apache.http.HttpHeaders
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
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

object HttpUtil {

    private val log = KotlinLogging.logger {}

    var proxy: Proxy? = null
    private const val filePartLength = 2 * 1024 * 1024L
    private val pool = ThreadPoolExecutor(5, 10, 1L, TimeUnit.MINUTES,
        LinkedBlockingQueue(20),
        object : ThreadFactory {
            private val counter = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "Downloader#${counter.getAndIncrement()}").also {
                    it.isDaemon = true
                }
            }
        })

    fun download(tgFile: org.telegram.telegrambots.meta.api.objects.File, file: File): File {
        return download(tgFile.getFileUrl(ContextHolder.telegramBotClient.botToken), file, true)
    }

    fun download(url: String, file: File, enableProxy: Boolean = false): File {
        val start = System.nanoTime()

        getRemoteFileSize(url).thenCompose { size ->
            if (size != null) {
                if (size < 1024 * 1024 * 100) multiPartDownload(url, file, size, enableProxy)
                else throw ImSyncBotRuntimeException("The file is too large: $size")
            } else {
                singleDownload(url, file, enableProxy)
            }
        }.join()

        if (file.length() <= 0) throw ImSyncBotRuntimeException("File is null: $url")
        val sizeOfMb = file.length() / 1024.0 / 1024
        val timeOfSeconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) / 1000.0
        val speed = sizeOfMb / timeOfSeconds
        log.info { "Downloaded ${file.name} ${String.format("%.3f", sizeOfMb)} MB in ${String.format("%.2f", timeOfSeconds)} s (${String.format("%.2f", speed)} MB/s)" }
        return file
    }

    private fun multiPartDownload(url: String, file: File, size: Long, enableProxy: Boolean = false): CompletableFuture<Void> {
        createFile(file, size)
        val partLength = if (size <= filePartLength) {
            return singleDownload(url, file, enableProxy)
        } else if (size > filePartLength * 20) {
            filePartLength * 4
        } else {
            filePartLength
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
                    .header(HttpHeaders.RANGE, "bytes=$offset-${length?.let { offset + length - 1 } ?: ""}")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream())
            .thenAccept { response ->
                response.body().write(file, offset)
                response.headers().firstValue(HttpHeaders.CONTENT_RANGE).ifPresent { contentRange ->
                    val range = contentRange.substringBefore("/").replace("bytes ", "")
                    val ranges = range.split("-")
                    val left = ranges[0].toLong()
                    val right = ranges[1].toLong()

                    val sizeOfMb = (right - left) / 1024.0 / 1024
                    val timeOfSeconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) / 1000.0
                    val speed = sizeOfMb / timeOfSeconds
                    log.debug {
                        "Downloaded ${file.name.substringBeforeLast(".")} part of $range ${String.format("%.3f", sizeOfMb)} MB in ${
                            String.format("%.2f", timeOfSeconds)
                        } s (${String.format("%.2f", speed)} MB/s)"
                    }
                }
            }
    }

    private fun getRemoteFileSize(url: String, enableProxy: Boolean = false): CompletableFuture<Long?> {
        return newHttpClient(enableProxy).sendAsync(
            HttpRequest
                .newBuilder(URI.create(url))
                .header(HttpHeaders.RANGE, "bytes=0-1")
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
                res.headers().firstValue(HttpHeaders.CONTENT_RANGE).map { it.substringAfterLast('/').toLong() }.orElse(null)
            }
        }
    }

    private fun newHttpClient(enableProxy: Boolean = false): HttpClient {
        return if (proxy != null && enableProxy) {
            val address = proxy!!.address()
            HttpClient
                .newBuilder()
                .executor(pool)
                .proxy(ProxySelector.of(InetSocketAddress(address.hostname, address.port)))
                .build()
        } else HttpClient.newHttpClient()
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