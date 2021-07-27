package kurenai.mybot.qq

import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.*

@ConfigurationProperties(prefix = "bot.qq")
//@PropertySource(factory = YamlPropertySourceFactory::class)
class QQBotProperties {
    var account = 0L
    var password = ""
    var protocol = MiraiProtocol.ANDROID_PAD
    var filter = Filter()

    class Filter {
        var group: List<Long> = Collections.emptyList()
    }
}