package kurenai.imsyncbot

import kotlinx.serialization.Serializable
import kurenai.imsyncbot.utils.yamlMapper
import net.mamoe.mirai.utils.BotConfiguration
import java.io.File
import java.net.Proxy

/**
 * 配置文件 v2
 * @author Kurenai
 * @since 3/27/2023 20:52:40
 */

@Serializable
open class ConfigPropertiesVersion(val version: Int? = null)

fun loadConfig(file: File): ConfigProperties? {
    val text = file.readText()
    val doBackup = { config: ConfigProperties ->
        runCatching {
            file.copyTo(File("${file.absolutePath}.bak"), false)
        }.recover {
            file.copyTo(File("${file.absolutePath}-${System.currentTimeMillis()}.bak"), false)
        }
        yamlMapper.writerWithDefaultPrettyPrinter().writeValue(file, config)
    }
    val jsonNode = yamlMapper.readTree(text)
    return when (jsonNode.get("version")?.asInt(0) ?: 0) {
        2 -> yamlMapper.treeToValue(jsonNode, ConfigProperties::class.java)
        1 -> runCatching {
            yamlMapper.treeToValue(jsonNode, ConfigPropertiesV1::class.java).migration().also {
                doBackup(it)
            }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()

        0 -> runCatching {
            yamlMapper.treeToValue(jsonNode, ConfigProperties::class.java)
        }.recover {
            yamlMapper.treeToValue(jsonNode, ConfigPropertiesV1::class.java).migration()
        }.getOrNull()?.also {
            doBackup(it)
        }

        else -> null
    }
}

private fun ConfigPropertiesV1.migration() = migrationToV2()

private fun ConfigPropertiesV1.migrationToV2() = ConfigProperties(
    enable = this@migrationToV2.enable,
    redis = this@migrationToV2.redis,
    bot = BotProperties(
        qq = this@migrationToV2.bot.qq,
        telegram = this@migrationToV2.bot.telegram,
        tgMsgFormat = this@migrationToV2.handler.tgMsgFormat,
        qqMsgFormat = this@migrationToV2.handler.qqMsgFormat,
        masterOfTg = this@migrationToV2.handler.masterOfTg,
        masterOfQq = this@migrationToV2.handler.masterOfQq,
        privateChat = this@migrationToV2.handler.privateChat,
        privateChatChannel = this@migrationToV2.handler.privateChatChannel,
        picToFileSize = this@migrationToV2.handler.picToFileSize,
        enableRecall = this@migrationToV2.handler.enableRecall,
    ),
    debug = this.debug
)

@Serializable
data class ConfigProperties(
    val enable: Boolean = true,
    val redis: RedisProperties = RedisProperties(),
    val bot: BotProperties = BotProperties(),
    val debug: Boolean = false,
) : ConfigPropertiesVersion(2) {
}

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
data class QQProperties(
    val account: Long = 0L,
    val password: String = "",
    val protocol: BotConfiguration.MiraiProtocol = BotConfiguration.MiraiProtocol.ANDROID_PAD,
    val host: String = "",
    val port: Int = 0,
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