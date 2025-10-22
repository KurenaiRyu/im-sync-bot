package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.FriendConfig
import kurenai.imsyncbot.domain.qqId
import kurenai.imsyncbot.domain.telegramGroupId
import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.withVT
import org.babyfish.jimmer.sql.kt.ast.expression.eq

object FriendConfigRepository {

    suspend fun findByQQ(id: Long): FriendConfig? = withVT {
        sqlClient.createQuery(FriendConfig::class) {
            where(table.qqId eq id)
            select(table)
        }.fetchOneOrNull()
    }

    suspend fun findByTG(id: Long): FriendConfig? = withVT {
        sqlClient.createQuery(FriendConfig::class) {
            where(table.telegramGroupId eq id)
            select(table)
        }.fetchOneOrNull()
    }

}