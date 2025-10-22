package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.FriendConfig
import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.withVT
import org.babyfish.jimmer.sql.kt.ast.expression.and
import org.babyfish.jimmer.sql.kt.ast.expression.eq

object FriendConfigRepository {

    suspend fun findByQQ(botId: Long, id: Long): FriendConfig? = withVT {
        sqlClient.createQuery(FriendConfig::class) {
            where(table.qqId eq id)
            select(table)
        }.fetchOneOrNull()
    }

    suspend fun findByTG(botId: Long, id: Long): FriendConfig? = withVT {
        sqlClient.createQuery(FriendConfig::class) {
            where(and(table.botId eq botId, table.telegramGroupId eq id))
            select(table)
        }.fetchOneOrNull()
    }

}