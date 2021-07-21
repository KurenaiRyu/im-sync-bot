package kurenai.mybot.telegram

import org.springframework.boot.context.properties.ConfigurationProperties
import org.telegram.telegrambots.bots.DefaultBotOptions

/**
 * @author liufuhong
 * @since 2021-06-30 14:13
 */
@ConfigurationProperties(prefix = "bot.telegram.proxy")
class ProxyProperties {
    val host = "localhost"
    val port = 1080
    val type = DefaultBotOptions.ProxyType.NO_PROXY
}