package kurenai.imsyncbot.telegram

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.telegram.telegrambots.bots.DefaultBotOptions

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
    var type = DefaultBotOptions.ProxyType.NO_PROXY
    var onlyDownloadTgFile = true
}