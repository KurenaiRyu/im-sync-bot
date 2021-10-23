package kurenai.imsyncbot.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.utils.downloader.DefaultDownloader
import kurenai.imsyncbot.utils.downloader.MultiPartDownloader
import mu.KotlinLogging
import org.apache.http.HttpHeaders
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.util.EntityUtils
import org.springframework.util.StreamUtils
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.security.KeyManagementException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object HttpUtil {

    private val log = KotlinLogging.logger {}

    const val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:89.0) Gecko/20100101 Firefox/89.0"
    private const val filePartLength = 1024 * 1024L
    private const val MAX_TIMEOUT = 7000
    private val connMgr: PoolingHttpClientConnectionManager = PoolingHttpClientConnectionManager()
    private val requestConfig: RequestConfig
    private val client = buildClient()
    private val pool = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2, object : ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "Download Thread#${counter.getAndIncrement()}").also {
                it.isDaemon = true
            }
        }
    })

    @Throws(NoSuchAlgorithmException::class, KeyStoreException::class, KeyManagementException::class, IOException::class)
    fun download(url: String): ByteArray {
        val res = client.execute(RequestBuilder.get(url).build())
        return EntityUtils.toByteArray(res.entity)
    }

    @Throws(NoSuchAlgorithmException::class, KeyStoreException::class, KeyManagementException::class, IOException::class)
    fun download(url: String, file: File) {
        val start = System.nanoTime()
        getRemoteFileSize(url)?.let {
            multiPartDownload(url, file, it)
        } ?: run {
            createFile(file)
            DefaultDownloader(url, file, client).run()
        }
        val sizeOfMb = file.length() / 1024.0 / 1024
        val timeOfSeconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) / 1000.0
        val speed = sizeOfMb / timeOfSeconds
        log.info { "Downloaded ${file.name} ${String.format("%.3f", sizeOfMb)} MB in ${String.format("%.2f", timeOfSeconds)} s (${String.format("%.2f", speed)} MB/s)" }
    }

    @Throws(NoSuchAlgorithmException::class, KeyStoreException::class, KeyManagementException::class, IOException::class)
    private fun multiPartDownload(url: String, file: File, size: Long) {
        createFile(file, size)
        val partLength = if (size <= filePartLength) {
            return DefaultDownloader(url, file, client).run()
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
    }

    @Throws(NoSuchAlgorithmException::class, KeyStoreException::class, KeyManagementException::class, IOException::class)
    suspend fun get(url: String): String {
        val client = buildClient()
        return withContext(Dispatchers.IO) {
            StreamUtils.copyToString(client.execute(RequestBuilder.get(url).build()).entity.content, StandardCharsets.UTF_8)
        }
    }

    // 创建SSL安全连接
    @Throws(NoSuchAlgorithmException::class, KeyStoreException::class, KeyManagementException::class)
    private fun createSSLConnSocketFactory(): SSLConnectionSocketFactory {
        val sslContext = SSLContextBuilder().loadTrustMaterial(null, TrustStrategy { _, _ -> true }).build()
        return SSLConnectionSocketFactory(sslContext) { _, _ -> true }
    }

    @Throws(NoSuchAlgorithmException::class, KeyStoreException::class, KeyManagementException::class)
    private fun buildClient(): CloseableHttpClient {
        return HttpClients.custom().setSSLSocketFactory(createSSLConnSocketFactory()).setConnectionManager(connMgr)
            .setDefaultRequestConfig(requestConfig).build()
    }

    private fun getRemoteFileSize(url: String): Long? {
        return try {
            val res = client.execute(
                RequestBuilder
                    .get(url)
                    .addHeader(HttpHeaders.RANGE, "bytes=0-1")
                    .build()
            )
            val code = res.statusLine.statusCode
            if (code != 206) {
                log.debug { "Get remote file size fail: [$code] ${res.statusLine.reasonPhrase}" }
                null
            } else {
                res.getFirstHeader(HttpHeaders.CONTENT_RANGE)?.value?.substringAfterLast('/')?.toLong()
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

    init {
        // 设置连接池大小
        connMgr.setMaxTotal(100)
        connMgr.setDefaultMaxPerRoute(connMgr.getMaxTotal())
        // 在提交请求之前 测试连接是否可用
        connMgr.setValidateAfterInactivity(1000)
        val configBuilder = RequestConfig.custom()
        // 设置连接超时
        configBuilder.setConnectTimeout(MAX_TIMEOUT)
        // 设置读取超时
        configBuilder.setSocketTimeout(MAX_TIMEOUT)
        // 设置从连接池获取连接实例的超时
        configBuilder.setConnectionRequestTimeout(MAX_TIMEOUT)
        // 在提交请求之前 测试连接是否可用
//        configBuilder.setStaleConnectionCheckEnabled(true);
        requestConfig = configBuilder.build()
    }
}