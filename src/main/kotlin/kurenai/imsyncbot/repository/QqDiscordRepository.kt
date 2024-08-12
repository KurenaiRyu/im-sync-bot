package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.QQDiscord
import org.springframework.data.jpa.repository.JpaRepository

/**
 * @author Kurenai
 * @since 2023/6/20 23:02
 */

interface QqDiscordRepository : JpaRepository<QQDiscord, Long> {

    fun findByQqGrpIdAndQqMsgId(qqGrpId: Long, qqId: Int): QQDiscord?

}