package kurenai.imsyncbot.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "GROUP_CONFIG")
class GroupConfig(
    var qqGroupId: Long,
    var name: String,
    var telegramGroupId: Long? = null,
    var discordChannelId: Long? = null,
    var status: String? = null,
    @Id var id: Long? = null,
)
