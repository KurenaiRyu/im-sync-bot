package kurenai.imsyncbot.handler.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bot.handler.forward")
//@PropertySource(factory = YamlPropertySourceFactory::class)
class ForwardHandlerProperties {

    var tgMsgFormat = "\$name: \$msg"
    var qqMsgFormat = "\$name: \$msg"
    var masterOfTg = emptyList<Long>()
    var masterOfQq = emptyList<Long>()
    var masterNameOfTg = "masterUsername"
    var group = Group()
    var privateChat = 0L
    var privateChatChannel = 0L
    var picToFileSize = 10L
    var enableRecall = true

    class Group {
        var defaultQQ: Long = 0
        var defaultTelegram: Long = 0
    }
}