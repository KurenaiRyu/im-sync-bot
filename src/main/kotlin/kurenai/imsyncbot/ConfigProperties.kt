package kurenai.imsyncbot

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kurenai.imsyncbot.utils.json
import net.mamoe.mirai.utils.BotConfiguration
import java.io.File
import java.net.Proxy

/**
 * 配置文件 v2
 * @author Kurenai
 * @since 6/22/2022 20:52:40
 */

@Serializable(with = ConfigPropertiesSerializer::class)
interface ConfigPropertiesInterface {
    val version: Int
}

object ConfigPropertiesSerializer : KSerializer<ConfigPropertiesInterface> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("kurenai.imsyncbot.ConfigPropertiesInterface") {
        element<String>("version")
    }

    override fun deserialize(decoder: Decoder): ConfigPropertiesInterface {
        decoder as JsonDecoder
        val version = decoder.decodeJsonElement().jsonObject["version"]?.jsonPrimitive?.intOrNull
        return if (version == 2) {
            decoder.decodeSerializableValue(ConfigProperties.serializer())
        } else {
            decoder.decodeSerializableValue(ConfigPropertiesV1.serializer())
        }
    }

    override fun serialize(encoder: Encoder, value: ConfigPropertiesInterface) {
        when (value) {
            is ConfigProperties -> encoder.encodeSerializableValue(ConfigProperties.serializer(), value)
            is ConfigPropertiesV1 -> encoder.encodeSerializableValue(ConfigPropertiesV1.serializer(), value)
        }
    }

}

fun loadConfig(file: File) = when (val config = json.decodeFromString(ConfigPropertiesSerializer, file.readText())) {
    is ConfigPropertiesV1 -> {
        runCatching {
            file.copyTo(File("${file.name}.bak"), false)
        }.recover {
            file.copyTo(File("${file.name}-${System.currentTimeMillis()}.bak"), false)
        }
        config.migration().also {
            file.writeText(json.encodeToString(ConfigProperties.serializer(), it))
        }
    }

    is ConfigProperties -> config
    else -> null
}

private fun ConfigPropertiesV1.migration() = migrationToV2()

private fun ConfigPropertiesV1.migrationToV2() = ConfigProperties(
    enable = this.enable,
    redis = this.redis,
    bot = BotProperties(
        qq = this.bot.qq,
        telegram = this.bot.telegram,
        tgMsgFormat = this.handler.tgMsgFormat,
        qqMsgFormat = this.handler.qqMsgFormat,
        masterOfTg = this.handler.masterOfTg,
        masterOfQq = this.handler.masterOfQq,
        privateChat = this.handler.privateChat,
        privateChatChannel = this.handler.privateChatChannel,
        picToFileSize = this.handler.picToFileSize,
        enableRecall = this.handler.enableRecall,
    ),
    debug = this.debug
)

@Serializable
data class ConfigProperties(
    val enable: Boolean = true,
    val redis: RedisProperties = RedisProperties(),
    val bot: BotProperties = BotProperties(),
    val debug: Boolean = false,
) : ConfigPropertiesInterface {
    override val version: Int = 2
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
)

@Serializable
data class TelegramProperties(
    val token: String = "",
    val username: String = "",
    val baseUrl: String = "https://api.telegram.org",
    val proxy: ProxyProperties = ProxyProperties()
)

@Serializable
data class ProxyProperties(
    val host: String = "",
    val port: Int = 0,
    val type: Proxy.Type = Proxy.Type.DIRECT
)