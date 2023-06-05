package kurenai.imsyncbot.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import kurenai.imsyncbot.snowFlake
import net.mamoe.mirai.message.data.MessageSource
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * @author Kurenai
 * @since 2023/6/3 16:38
 */

@Entity
@Table(
    name = "QQ_MESSAGE", indexes = [
        Index(
            name = "QQ_MESSAGE_MESSAGE_ID_BOT_ID_OBJ_ID_TYPE_uindex",
            columnList = "messageId DESC, botId, objId, type",
            unique = true
        )
    ]
)
class QQMessage(
    var messageId: Int,
    var botId: Long,
    var objId: Long,
    var sender: Long,
    var target: Long,
    var type: QQMessageType,
    @Column(name = "JSON_TXT") @Lob var json: String,
    var handled: Boolean,
    var msgTime: LocalDateTime,
    @Id var id: Long = snowFlake.nextId()
) {
    enum class QQMessageType {
        GROUP, GROUP_TEMP, FRIEND
    }
}

fun MessageSource.getLocalDateTime(): LocalDateTime =
    LocalDateTime.ofEpochSecond(this.time.toLong(), 0, ZoneOffset.ofHours(8))


