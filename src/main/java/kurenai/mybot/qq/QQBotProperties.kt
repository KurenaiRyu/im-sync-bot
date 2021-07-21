package kurenai.mybot.qq

import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import org.springframework.boot.context.properties.ConfigurationProperties
import kotlin.properties.Delegates

@ConfigurationProperties(prefix = "bot.qq")
class QQBotProperties {
    var account by Delegates.notNull<Long>()
    lateinit var password: String
    var protocol: MiraiProtocol = MiraiProtocol.ANDROID_PAD
    lateinit var filter: Filter

    class Filter {
        lateinit var qq: List<Long>
    }
}