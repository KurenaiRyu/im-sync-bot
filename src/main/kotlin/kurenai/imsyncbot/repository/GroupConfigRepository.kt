package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.domain.qqGroupId
import kurenai.imsyncbot.utils.withVT
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn

/**
 * @author Kurenai
 * @since 2023/6/18 21:17
 */

object GroupConfigRepository : BaseRepository<GroupConfig, Long>() {

    suspend fun findAll() = withVT {
        createQuery<GroupConfig> {
            select(table)
        }.execute()
    }

    suspend fun findByQqGroupId(groupId: Long): GroupConfig? = withVT {
        createQuery<GroupConfig> {
            where(table.qqGroupId eq groupId)
            select(table)
        }.fetchOneOrNull()
    }

    suspend fun findAllByQqGroupIdIn(groupIds: Collection<Long>): Collection<GroupConfig> = withVT {
        createQuery<GroupConfig> {
            where(table.qqGroupId valueIn groupIds)
            select(table)
        }.execute()
    }

}