package kurenai.imsyncbot.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.*

@ConfigurationProperties(prefix = "bot")
class BotProperties {

    var ban = Ban()

    class Ban {
        var group: List<Long> = Collections.emptyList()
        var member: List<Long> = Collections.emptyList()
    }

}