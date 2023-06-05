package kurenai.imsyncbot.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import kurenai.imsyncbot.snowFlake

/**
 * @author Kurenai
 * @since 2023/6/4 18:08
 */

@Entity
@Table(
    name = "QQ_TG", indexes = [
        Index(name = "QQ_TG_GRP_ID_TG_MSG_ID_uindex", columnList = "tgGrpId, tgMsgId DESC", unique = true)
    ]
)
class QQTg(
    var qqId: Long,
    var qqMsgId: Int,
    var tgGrpId: Long,
    var tgMsgId: Long,
    @Id var id: Long = snowFlake.nextId()
)