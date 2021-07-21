package kurenai.mybot.handler.config

import kurenai.mybot.handler.HandlerProperties
import lombok.Getter
import lombok.Setter
import org.springframework.boot.context.properties.ConfigurationProperties

@Getter
@Setter
@ConfigurationProperties(prefix = "bot.handler.anti-mini-app")
class AntiMiniAppHandlerProperties : HandlerProperties() {
    lateinit var ignoreGroup: List<Long>
}