package kurenai.imsyncbot.utils.downloader

import kurenai.imsyncbot.ImSyncBotRuntimeException
import kurenai.imsyncbot.utils.HttpUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.CountDownLatch

class DefaultDownloader(
    url: String,
    file: File,
    private val httpClient: OkHttpClient,
    private val countDownLatch: CountDownLatch? = null,
) : Downloader(url, file) {

    override fun run() {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use {
            it.body?.let { body ->
                writeToFile(body.byteStream())
            } ?: throw ImSyncBotRuntimeException("Body is null: ${HttpUtil.getResponseInfo(it)}")
        }
        countDownLatch?.countDown()
    }
}