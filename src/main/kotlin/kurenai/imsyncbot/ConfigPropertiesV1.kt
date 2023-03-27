package kurenai.imsyncbot

import kotlinx.serialization.Serializable
import net.mamoe.mirai.utils.BotConfiguration
import java.net.Proxy

/**
 * 配置文件
 * @author Kurenai
 * @since 6/22/2022 20:52:40
 */

@Serializable
data class ConfigPropertiesV1(
    val enable: Boolean = true,
    val redis: RedisProperties = RedisProperties(),
    val bot: BotPropertiesV1 = BotPropertiesV1(),
    val handler: HandlerPropertiesV1 = HandlerPropertiesV1(),
    val debug: Boolean = false,
) : ConfigPropertiesInterface {
    override val version: Int = 1
}

@Serializable
data class BotPropertiesV1(
    val qq: QQProperties = QQProperties(),
    val telegram: TelegramProperties = TelegramProperties()
)

@Serializable
data class HandlerPropertiesV1(
    val tgMsgFormat: String = "\$name: \$msg",
    val qqMsgFormat: String = "\$name: \$msg",
    val masterOfTg: Long = 0L,
    val masterOfQq: Long = 0L,
    val privateChat: Long = 0L,
    val privateChatChannel: Long = 0L,
    val picToFileSize: Long = 2,
    val enableRecall: Boolean = true,
)