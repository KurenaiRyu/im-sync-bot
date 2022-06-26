package kurenai.imsyncbot.qq

import io.quarkus.arc.config.ConfigProperties
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol

@ConfigProperties(prefix = "bot.qq")
class QQBotProperties {
    var account = 0L
    var password = ""
    var protocol = MiraiProtocol.ANDROID_PAD
}