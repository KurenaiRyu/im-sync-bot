package kurenai.imsyncbot.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import kurenai.imsyncbot.snowFlake

/**
 * @author Kurenai
 * @since 2023/6/20 22:56
 */

@Entity
@Table(
    name = "QQ_DISCORD", indexes = [
        Index(
            name = "QQ_DISCORD_CHANNEL_ID_DISCORD_MSG_ID_uindex",
            columnList = "discordChannelId, discordMsgId DESC",
            unique = true
        )
    ]
)
class QQDiscord(
    var qqGrpId: Long,
    var qqMsgId: Int,
    var discordChannelId: Long,
    var discordMsgId: Long,
    @Id var id: Long = snowFlake.nextId()
)