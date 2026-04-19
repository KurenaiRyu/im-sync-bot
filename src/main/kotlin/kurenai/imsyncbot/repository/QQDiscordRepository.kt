package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.QQDiscord
import kurenai.imsyncbot.domain.qqGroupId
import kurenai.imsyncbot.domain.qqMsgId
import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.withVT
import org.babyfish.jimmer.sql.kt.ast.expression.eq

/**
 * @author Kurenai
 * @since 2023/6/3 16:36
 */

object QQDiscordRepository : BaseRepository<QQDiscord, Long>() {

    suspend fun findByQQ(messageId: Int, groupId: Long): List<QQDiscord> = withVT {
        sqlClient.createQuery(QQDiscord::class) {
            where(
                table.qqMsgId eq messageId,
                table.qqGroupId eq groupId
            )
            select(table)
        }.execute()
    }

}