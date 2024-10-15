package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.domain.qqGroupId
import kurenai.imsyncbot.domain.tgMsgId
import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.withIO
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.data.jpa.repository.JpaRepository

/**
 * @author Kurenai
 * @since 2023/6/18 21:17
 */

object GroupConfigRepository : BaseRepository<GroupConfig, Long>() {

    suspend fun findAll() = withIO {
        createQuery<GroupConfig> {
            select(table)
        }.execute()
    }

    suspend fun findByQqGroupId(groupId: Long): GroupConfig? = withIO {
        createQuery<GroupConfig> {
            where(table.qqGroupId eq groupId)
            select(table)
        }.fetchOneOrNull()
    }

    suspend fun findAllByQqGroupIdIn(groupIds: Collection<Long>): Collection<GroupConfig> = withIO {
        createQuery<GroupConfig> {
            where(table.qqGroupId valueIn groupIds)
            select(table)
        }.execute()
    }

}