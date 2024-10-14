package kurenai.imsyncbot.domain

import kurenai.imsyncbot.jimmer.SnowFlakeGenerator
import net.mamoe.mirai.message.data.MessageSourceKind
import org.babyfish.jimmer.sql.*
import java.time.LocalDateTime

@Entity
@Table(name = "QQ_MESSAGE")
interface QQMessage {

    @Id
    @GeneratedValue(generatorType = SnowFlakeGenerator::class)
    val id: Long
    @Key
    val messageId: Int
    @Key
    val botId: Long
    val fromId: Long
    @Key
    val targetId: Long

    val type: MessageSourceKind

    val status: MessageStatus

    @Column(name = "JSON_TXT")
    val json: String
    val handled: Boolean?
    val time: LocalDateTime
    @Version
    val version: Int

}