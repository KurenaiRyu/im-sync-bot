package kurenai.mybot.handler.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.util.*

@ConstructorBinding
@ConfigurationProperties(prefix = "bot.handler.forward")
//@PropertySource(factory = YamlPropertySourceFactory::class)
class ForwardHandlerProperties {

    var tgMsgFormat = "\$name: \$msg"
    var qqMsgFormat = "\$name: \$msg"
    var masterOfTg = 0L
    var masterOfQq = 0L
    var group = Group()
    var member = Member()

    class Group {
        var defaultQQ: Long = 0
        var defaultTelegram: Long = 0
        var qqTelegram = HashMap<Long, Long>()
        var telegramQq = HashMap<Long, Long>()

        init {
            if (qqTelegram.isNotEmpty()) {
                qqTelegram.entries.forEach {
                    telegramQq.putIfAbsent(it.value, it.key)
                }
            }
            if (telegramQq.isNotEmpty()) {
                telegramQq.entries.forEach {
                    qqTelegram.putIfAbsent(it.value, it.key)
                }
            }
        }
    }

    class Member {
        var bindingName: Map<Long, String> = Collections.emptyMap()
    }
}