package kurenai.imsyncbot.jimmer.domain

import kurenai.imsyncbot.domain.MessageStatus
import kurenai.imsyncbot.jimmer.SnowFlakeGenerator
import net.mamoe.mirai.message.data.MessageSourceKind
import org.babyfish.jimmer.sql.Column
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Key
import org.babyfish.jimmer.sql.meta.UUIDIdGenerator
import java.time.LocalDateTime

@Entity
interface QQMessage {

    @Id
    @GeneratedValue(generatorType = SnowFlakeGenerator::class)
    val id: Long
    @Key
    val messageId: Int
    @Key
    val botId: Long
    @Key
    val fromId: Long
    @Key
    val targetId: Long

    val type: MessageSourceKind

    val status: MessageStatus

    @Column(name = "JSON_TXT")
    val json: String?
    val handled: Boolean?
    val time: LocalDateTime

}