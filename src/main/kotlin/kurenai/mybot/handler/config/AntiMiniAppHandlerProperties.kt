package kurenai.mybot.handler.config

import kurenai.mybot.handler.HandlerProperties
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bot.handler.anti-mini-app")
class AntiMiniAppHandlerProperties : HandlerProperties() {
    lateinit var ignoreGroup: List<Long>
}