package kurenai.imsyncbot

import net.mamoe.mirai.utils.BotConfiguration
import java.net.Proxy

/**
 * 配置文件
 * @author Kurenai
 * @since 6/22/2022 20:52:40
 */

data class ConfigProperties(
    val enable: Boolean = true,
    val redis: RedisProperties = RedisProperties(),
    val bot: BotProperties = BotProperties(),
    val handler: HandlerProperties = HandlerProperties(),
    val debug: Boolean = false
)

data class RedisProperties(
    val url: String? = null,
    val host: String = "redis",
    val port: Int = 6379,
    val database: Int = 0
)

data class BotProperties(
    val qq: QQProperties = QQProperties(),
    val telegram: TelegramProperties = TelegramProperties()
)

data class HandlerProperties(
    val tgMsgFormat: String = "\$name: \$msg",
    val qqMsgFormat: String = "\$name: \$msg",
    val masterOfTg: Long = 0L,
    val masterOfQq: Long = 0L,
    val privateChat: Long = 0L,
    val privateChatChannel: Long = 0L,
    val picToFileSize: Long = 2,
    val enableRecall: Boolean = true,
)

data class QQProperties(
    val account: Long = 0L,
    val password: String = "",
    val protocol: BotConfiguration.MiraiProtocol = BotConfiguration.MiraiProtocol.ANDROID_PAD,
)

data class TelegramProperties(
    val token: String = "",
    val username: String = "",
    val baseUrl: String = "https://api.telegram.org",
    val proxy: ProxyProperties = ProxyProperties()
)

data class ProxyProperties(
    val host: String = "",
    val port: Int = 0,
    val type: Proxy.Type = Proxy.Type.DIRECT
)