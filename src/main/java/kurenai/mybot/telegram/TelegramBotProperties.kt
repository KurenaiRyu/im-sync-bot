package kurenai.mybot.telegram

import lombok.Data
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @author liufuhong
 * @since 2021-06-30 14:10
 */
@Data
@ConfigurationProperties(prefix = "bot.telegram")
class TelegramBotProperties {
    val token: String? = null
    val username: String? = null
}