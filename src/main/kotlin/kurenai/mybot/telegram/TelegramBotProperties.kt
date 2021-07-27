package kurenai.mybot.telegram

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @author Kurenai
 * @since 2021-06-30 14:10
 */
@ConfigurationProperties(prefix = "bot.telegram")
//@PropertySource(factory = YamlPropertySourceFactory::class)
class TelegramBotProperties {
    var token = ""
    var username = ""
    var baseUrl = ""
}