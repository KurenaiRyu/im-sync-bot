package kurenai.imsyncbot.telegram

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @author Kurenai
 * @since 2021-06-30 14:10
 */
@ConfigurationProperties(prefix = "bot.telegram")
//@PropertySource(factory = YamlPropertySourceFactory::class)
class TelegramBotProperties {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.telegram.org"
    }

    var token = ""
    var username = ""
    var baseUrl = DEFAULT_BASE_URL
    var imgBaseUrl: String? = null
    var updateBaseUrl: String? = null
}