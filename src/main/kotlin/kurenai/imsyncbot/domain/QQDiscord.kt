package kurenai.imsyncbot.domain

import kurenai.imsyncbot.jimmer.SnowFlakeGenerator
import org.babyfish.jimmer.sql.*


/**
 * @author Kurenai
 */
@Entity
@Table(name = "QQ_DISCORD")
interface QQDiscord {
    @Id
    @GeneratedValue(generatorType = SnowFlakeGenerator::class)
    val id: Long
    val qqMsgId: Int
    val qqGroupId: Long


    @Key
    val guildId: Long

    @Key
    val channelId: Long

    @Key
    val messageId: Long

    @Version
    val version: Int
}