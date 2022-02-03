package kurenai.imsyncbot.telegram

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.net.Proxy

/**
 * @author Kurenai
 * @since 2021-06-30 14:13
 */

@ConstructorBinding
@ConfigurationProperties(prefix = "bot.telegram.proxy")
//@PropertySource(factory = YamlPropertySourceFactory::class)
class ProxyProperties {
    var host = "localhost"
    var port = 1080
    var type = Proxy.Type.DIRECT
    var onlyDownloadTgFile = true
}