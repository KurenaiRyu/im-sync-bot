package kurenai.imsyncbot

import kotlinx.serialization.Serializable
import net.mamoe.mirai.utils.BotConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.Proxy

/**
 * 配置文件
 * @author Kurenai
 * @since 3/27/2023 20:52:40
 */


@ConfigurationProperties(prefix = "im-sync-bot")
@Serializable
data class ConfigProperties(
    val enable: Boolean = true,
    val redis: RedisProperties = RedisProperties(),
    val bot: BotProperties = BotProperties(),
    val debug: Boolean = false,
)

@Serializable
data class RedisProperties(
    val url: String? = null,
    val host: String = "redis",
    val port: Int = 6379,
    val database: Int = 0
)

@Serializable
data class BotProperties(
    val qq: QQProperties = QQProperties(),
    val telegram: TelegramProperties = TelegramProperties(),
    val discord: DiscordProperties = DiscordProperties(),
    val satori: SatoriProperties = SatoriProperties(),
    val tgMsgFormat: String = "\$name: \$msg",
    val qqMsgFormat: String = "\$name: \$msg",
    val masterOfTg: Long = 0L,
    val masterOfQq: Long = 0L,
    val privateChat: Long = 0L,
    val privateChatChannel: Long = 0L,
    val picToFileSize: Long = 2,
    val enableRecall: Boolean = true,
)

@Serializable
@Deprecated("No longer needed")
data class QQProperties(
    val host: String = "localhost",
    val port: Int = 9000,
    val token: String = "",
    val account: Long = 0L,
    val password: String = "",
    val protocol: BotConfiguration.MiraiProtocol = BotConfiguration.MiraiProtocol.ANDROID_PAD,
)

@Serializable
data class SatoriProperties(
    val host: String? = null,
    val port: Int? = null,
    val path: String? = null,
    val version: String? = null,
    val token: String? = null,
)

@Serializable
data class TelegramProperties(
    val token: String = "",
    val username: String = "",
    val baseUrl: String = "https://api.telegram.org",
    val proxy: ProxyProperties? = null,
    val apiId: Int? = null,
    val apiHash: String? = null,
)

@Serializable
data class DiscordProperties(
    val token: String? = null
)

@Serializable
data class ProxyProperties(
    val host: String = "",
    val port: Int = 0,
    val type: Proxy.Type = Proxy.Type.DIRECT
)