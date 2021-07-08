package kurenai.mybot.util;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.*;
import javax.swing.text.html.parser.Entity;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class HttpUtil {

    public static final  String                             UA          = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:89.0) Gecko/20100101 Firefox/89.0";
    private static final int                                MAX_TIMEOUT = 7000;
    private static final PoolingHttpClientConnectionManager connMgr;
    private static final RequestConfig                      requestConfig;

    static {
        // 设置连接池
        connMgr = new PoolingHttpClientConnectionManager();
        // 设置连接池大小
        connMgr.setMaxTotal(100);
        connMgr.setDefaultMaxPerRoute(connMgr.getMaxTotal());
        // 在提交请求之前 测试连接是否可用
        connMgr.setValidateAfterInactivity(1000);

        RequestConfig.Builder configBuilder = RequestConfig.custom();
        // 设置连接超时
        configBuilder.setConnectTimeout(MAX_TIMEOUT);
        // 设置读取超时
        configBuilder.setSocketTimeout(MAX_TIMEOUT);
        // 设置从连接池获取连接实例的超时
        configBuilder.setConnectionRequestTimeout(MAX_TIMEOUT);
        // 在提交请求之前 测试连接是否可用
//        configBuilder.setStaleConnectionCheckEnabled(true);
        requestConfig = configBuilder.build();
    }

    public static byte[] getImage(String url) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
        CloseableHttpClient client  = buildClient();
        CloseableHttpResponse res = client.execute(RequestBuilder.get(url).build());
        HttpEntity entity           = res.getEntity();
        return EntityUtils.toByteArray(entity);
    }

    // 创建SSL安全连接
    private static SSLConnectionSocketFactory createSSLConnSocketFactory() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true).build();
        return new SSLConnectionSocketFactory(sslContext, (a, b) -> true);
    }

    private static CloseableHttpClient buildClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return HttpClients.custom().setSSLSocketFactory(createSSLConnSocketFactory()).setConnectionManager(connMgr).setDefaultRequestConfig(requestConfig).build();
    }

}
