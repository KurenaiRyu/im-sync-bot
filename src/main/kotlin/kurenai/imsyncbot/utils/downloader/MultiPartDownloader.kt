package kurenai.imsyncbot.utils.downloader

import mu.KotlinLogging
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.impl.client.CloseableHttpClient
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MultiPartDownloader(
    url: String,
    file: File,
    offset: Long,
    private val length: Long,
    private val httpClient: CloseableHttpClient,
    private val countDownLatch: CountDownLatch,
    private val isFinal: Boolean = length == 0L,
) : Downloader(url, file, offset) {

    private val log = KotlinLogging.logger {}

    constructor(
        url: String,
        file: File,
        offset: Long,
        httpClient: CloseableHttpClient,
        countDownLatch: CountDownLatch,
    ) : this(url, file, offset, 0, httpClient, countDownLatch, true)

    override fun run() {
        val start = System.nanoTime()
        val rightOffset = offset + length - 1
        val response = httpClient.execute(
            RequestBuilder
                .get(url)
                .addHeader(HttpHeaders.RANGE, "bytes=$offset-${if (isFinal) "" else rightOffset}")
                .build()
        )
        writeToFile(response.entity.content)
        val time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        response.getFirstHeader(HttpHeaders.CONTENT_LENGTH)?.let {
            val length = it.value.toLong()
            val speed = length / 1024.0 / 1024 / time * 1000
            val sizeOfKb = length / 1024
            log.debug { "Download ${file.name} part of $offset-$rightOffset ($sizeOfKb kb) in $time ms (${String.format("%.2f", speed)} Mbit/s)" }
        }
        countDownLatch.countDown()
    }
}