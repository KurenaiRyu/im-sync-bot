package kurenai.imsyncbot.handler.config

import kurenai.imsyncbot.handler.HandlerProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.*

@ConfigurationProperties(prefix = "bot.handler.anti-mini-app")
class AntiMiniAppHandlerProperties : HandlerProperties() {
    var ignoreGroup: List<Long> = Collections.emptyList()
}