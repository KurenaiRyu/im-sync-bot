package kurenai.mybot.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.security.KeyManagementException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException

object HttpUtil {
    const val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:89.0) Gecko/20100101 Firefox/89.0"
    private const val MAX_TIMEOUT = 7000
    private val connMgr: PoolingHttpClientConnectionManager = PoolingHttpClientConnectionManager()
    private val requestConfig: RequestConfig

    @Throws(NoSuchAlgorithmException::class, KeyStoreException::class, KeyManagementException::class, IOException::class)
    suspend fun download(url: String): ByteArray {
        val client = buildClient()
        return withContext(Dispatchers.IO) {
            val res = client.execute(RequestBuilder.get(url).build())
            EntityUtils.toByteArray(res.entity)
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