package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.QQTg
import org.springframework.data.jpa.repository.JpaRepository

/**
 * @author Kurenai
 * @since 2023/6/3 16:36
 */

interface QQTgRepository : JpaRepository<QQTg, Long> {

    fun findByTgGrpIdAndTgMsgId(tgGrpId: Long, tgMsgId: Long): QQTg?

    fun findByTgGrpIdAndTgMsgIdIn(tgGrpId: Long, tgMsgId: Collection<Long>): List<QQTg>

    fun findByQqId(qqId: Long): List<QQTg>

    fun findAllByTgMsgId(tgMsgId: Long): List<QQTg>

}