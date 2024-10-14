package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.QQTg
import kurenai.imsyncbot.domain.qqId
import kurenai.imsyncbot.domain.tgGrpId
import kurenai.imsyncbot.domain.tgMsgId
import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.withIO
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn

/**
 * @author Kurenai
 * @since 2023/6/3 16:36
 */

object QQTgRepository : BaseRepository<QQTg, Long>() {

    suspend fun findOneByTgGrpIdAndTgMsgId(tgGrpId: Long, tgMsgId: Long): QQTg? = withIO {
        sqlClient.createQuery(QQTg::class) {
            where(
                table.tgGrpId eq tgGrpId,
                table.tgMsgId eq tgMsgId,
                )
            select(table)
        }.fetchOneOrNull()
    }

    suspend fun findByTgGrpIdAndTgMsgIdIn(tgGrpId: Long, tgMsgId: Collection<Long>): List<QQTg> = withIO {
        sqlClient.createQuery(QQTg::class) {
            where(
                table.tgGrpId eq tgGrpId,
                table.tgMsgId valueIn tgMsgId,
            )
            select(table)
        }.execute()
    }

    suspend fun findByQqId(qqId: Long): List<QQTg> = withIO {
        sqlClient.createQuery(QQTg::class) {
            where(
                table.qqId eq qqId,
            )
            select(table)
        }.execute()
    }

    suspend fun findByTgMsgId(tgMsgId: Long): List<QQTg> = withIO {
        sqlClient.createQuery(QQTg::class) {
            where(
                table.tgMsgId eq tgMsgId,
            )
            select(table)
        }.execute()
    }

}