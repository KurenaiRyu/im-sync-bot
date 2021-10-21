package kurenai.imsyncbot.utils.downloader

import org.apache.http.client.methods.RequestBuilder
import org.apache.http.impl.client.CloseableHttpClient
import java.io.File
import java.util.concurrent.CountDownLatch

class DefaultDownloader(
    url: String,
    file: File,
    private val httpClient: CloseableHttpClient,
    private val countDownLatch: CountDownLatch? = null,
) : Downloader(url, file) {

    override fun run() {
        val response = httpClient.execute(RequestBuilder
            .get(url)
            .build())
        writeToFile(response.entity.content)
        countDownLatch?.countDown()
    }
}