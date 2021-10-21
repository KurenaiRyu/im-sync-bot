package kurenai.imsyncbot.handler.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.util.*

@ConstructorBinding
@ConfigurationProperties(prefix = "bot.handler.forward")
//@PropertySource(factory = YamlPropertySourceFactory::class)
class ForwardHandlerProperties {

    var tgMsgFormat = "\$name: \$msg"
    var qqMsgFormat = "\$name: \$msg"
    var masterOfTg = emptyList<Long>()
    var masterNameOfTg = ""
    var masterOfQq = emptyList<Long>()
    var group = Group()
    var member = Member()
    var privateChat = 0L
    var privateChatChannel = 0L
    var picToFileSize = 10L
    var enableRecall = true

    class Group {
        var defaultQQ: Long = 0
        var defaultTelegram: Long = 0
        var qqTelegram: MutableMap<Long, Long> = Collections.emptyMap()
        var telegramQq: MutableMap<Long, Long> = Collections.emptyMap()
    }

    class Member {
        var bindingName: MutableMap<Long, String> = Collections.emptyMap()
    }
}