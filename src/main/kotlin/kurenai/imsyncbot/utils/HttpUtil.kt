package kurenai.imsyncbot.utils

import kurenai.imsyncbot.exception.ImSyncBotRuntimeException
import kurenai.imsyncbot.utils.downloader.DefaultDownloader
import kurenai.imsyncbot.utils.downloader.MultiPartDownloader
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.http.HttpHeaders
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

object HttpUtil {

    private val log = KotlinLogging.logger {}

    const val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:89.0) Gecko/20100101 Firefox/89.0"
    private const val filePartLength = 2 * 1024 * 1024L
    private val client = OkHttpClient()
    private val pool = ThreadPoolExecutor(5, 10, 1L, TimeUnit.MINUTES,
        LinkedBlockingQueue(20),
        object : ThreadFactory {
            private val counter = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "DownloadThread#${counter.getAndIncrement()}").also {
                    it.isDaemon = true
                }
            }
        })

    fun download(url: String, file: File) {
        val start = System.nanoTime()
        val countDown = CountDownLatch(1)
        getRemoteFileSize(url)?.let {
            if (it < 1024 * 1024 * 100) multiPartDownload(url, file, it, countDown)
            else throw ImSyncBotRuntimeException("The file is too large: $it")
        } ?: run {
            createFile(file)
            pool.execute(DefaultDownloader(url, file, client, countDown))
        }
        countDown.await(30, TimeUnit.SECONDS)
        if (file.length() <= 0) throw ImSyncBotRuntimeException("File is null: $url")
        val sizeOfMb = file.length() / 1024.0 / 1024
        val timeOfSeconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) / 1000.0
        val speed = sizeOfMb / timeOfSeconds
        log.info { "Downloaded ${file.name} ${String.format("%.3f", sizeOfMb)} MB in ${String.format("%.2f", timeOfSeconds)} s (${String.format("%.2f", speed)} MB/s)" }
    }

    private fun multiPartDownload(url: String, file: File, size: Long, upstreamCount: CountDownLatch) {
        createFile(file, size)
        val partLength = if (size <= filePartLength) {
            pool.execute(DefaultDownloader(url, file, client, upstreamCount))
            return
        } else if (size > filePartLength * 20) {
            filePartLength * 4
        } else {
            filePartLength
        }

        val moreOne = size % partLength != 0L
        val count = (size / partLength) + if (moreOne) 1 else 0
        val countDownLatch = CountDownLatch(count.toInt())
        var offset = 0L
        for (i in 2..count) {
            pool.execute(MultiPartDownloader(url, file, offset, partLength, client, countDownLatch))
            offset += partLength
        }
        if (moreOne) {
            pool.execute(MultiPartDownloader(url, file, offset, client, countDownLatch))
        }
        countDownLatch.await()
        upstreamCount.countDown()
    }

    fun get(url: String): String {
        return client.newCall(Request.Builder().url(url).build()).execute().use {
            it.body.toString()
        }
    }

    private fun getRemoteFileSize(url: String): Long? {
        return try {
            client.newCall(
                Request.Builder().url(url).header(HttpHeaders.RANGE, "bytes=0-1").build()
            ).execute().use { res ->
                val code = res.code
                if (code != 206) {
                    log.debug { "Get remote file size fail: ${getResponseInfo(res)}" }
                    null
                } else {
                    res.header(HttpHeaders.CONTENT_RANGE)?.substringAfterLast('/')?.toLong()
                }
            }
        } catch (e: Exception) {
            log.debug { "Get remote file size error: ${e.message}" }
            null
        }
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

    fun getResponseInfo(response: Response): String {
        return "[${response.code}]${response.message} ${response.body?.let { "{$it}" } ?: ""} (${response.request.url})"
    }
}