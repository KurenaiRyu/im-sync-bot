package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.UserConfig
import kurenai.imsyncbot.domain.qq
import kurenai.imsyncbot.domain.tg
import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.withIO
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * @author Kurenai
 * @since 2023/6/18 21:17
 */

object UserConfigRepository : BaseRepository<UserConfig, Long>() {

    suspend fun findByTgOrQQ(tgIds: List<Long>, qqIds: List<Long>): List<UserConfig> = withIO {
        if (tgIds.isEmpty() && qqIds.isEmpty()) return@withIO emptyList()

        createQuery<UserConfig> {
            if (tgIds.isNotEmpty()) where(table.tg valueIn tgIds)
            if (qqIds.isNotEmpty()) where(table.qq valueIn qqIds)
            select(table)
        }.execute()
    }

    suspend fun findAll() = withIO {
        createQuery<UserConfig> {
            select(table)
        }.execute()
    }

}