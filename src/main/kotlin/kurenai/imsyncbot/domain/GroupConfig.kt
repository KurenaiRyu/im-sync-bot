package kurenai.imsyncbot.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kurenai.imsyncbot.service.GroupStatus

@Entity
@Table(name = "GROUP_CONFIG")
class GroupConfig(
    var qqGroupId: Long,
    var name: String,
    var telegramGroupId: Long? = null,
    var discordChannelId: Long? = null,
    var status: HashSet<GroupStatus> = HashSet(),
    @Id var id: Long? = null,
)
