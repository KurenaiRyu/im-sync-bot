package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.UserConfig
import kurenai.imsyncbot.domain.qq
import kurenai.imsyncbot.domain.tg
import kurenai.imsyncbot.utils.withIO
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn

/**
 * @author Kurenai
 * @since 2023/6/18 21:17
 */

object UserConfigRepository : BaseRepository<UserConfig, Long>() {

    suspend fun findByTg(tg: Long): UserConfig? = withIO {
        createQuery<UserConfig> {
            where(table.tg eq tg)
            select(table)
        }.fetchOneOrNull()
    }

    suspend fun findByQQ(qq: Long): UserConfig? = withIO {
        createQuery<UserConfig> {
            where(table.qq eq qq)
            select(table)
        }.fetchOneOrNull()
    }

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