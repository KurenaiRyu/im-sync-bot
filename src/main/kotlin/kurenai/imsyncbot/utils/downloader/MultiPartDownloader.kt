package kurenai.imsyncbot.utils.downloader

import kurenai.imsyncbot.exception.ImSyncBotRuntimeException
import kurenai.imsyncbot.utils.HttpUtil
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.http.HttpHeaders
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MultiPartDownloader(
    url: String,
    file: File,
    offset: Long,
    private val length: Long,
    private val httpClient: OkHttpClient,
    private val countDownLatch: CountDownLatch,
    private val isFinal: Boolean = length == 0L,
) : Downloader(url, file, offset) {

    private val log = KotlinLogging.logger {}

    constructor(
        url: String,
        file: File,
        offset: Long,
        httpClient: OkHttpClient,
        countDownLatch: CountDownLatch,
    ) : this(url, file, offset, 0, httpClient, countDownLatch, true)

    override fun run() {
        val start = System.nanoTime()
        val rightOffset = offset + length - 1
        httpClient.newCall(
            Request.Builder()
                .url(url)
                .addHeader(HttpHeaders.RANGE, "bytes=$offset-${if (isFinal) "" else rightOffset}")
                .build()
        ).execute().use { response ->
            writeToFile(response.body?.byteStream() ?: throw ImSyncBotRuntimeException("Body is null: ${HttpUtil.getResponseInfo(response)}"))
            response.header(HttpHeaders.CONTENT_LENGTH)?.let {
                val sizeOfMb = length / 1024.0 / 1024
                val timeOfSeconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) / 1000.0
                val speed = sizeOfMb / timeOfSeconds
                log.debug {
                    "Downloaded ${file.name.substringBeforeLast(".")} part of $offset-$rightOffset ${String.format("%.3f", sizeOfMb)} MB in ${
                        String.format(
                            "%.2f",
                            timeOfSeconds
                        )
                    } s (${String.format("%.2f", speed)} MB/s)"
                }
            }
            countDownLatch.countDown()
        }
    }
}