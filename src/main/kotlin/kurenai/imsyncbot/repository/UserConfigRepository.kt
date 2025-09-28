package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.UserConfig
import kurenai.imsyncbot.domain.qq
import kurenai.imsyncbot.domain.tg
import kurenai.imsyncbot.utils.withVT
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.or
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn

/**
 * @author Kurenai
 * @since 2023/6/18 21:17
 */

object UserConfigRepository : BaseRepository<UserConfig, Long>() {

    suspend fun findByTg(tg: Long): UserConfig? = withVT {
        createQuery<UserConfig> {
            where(table.tg eq tg)
            select(table)
        }.fetchOneOrNull()
    }

    suspend fun findByQQ(qq: Long): UserConfig? = withVT {
        createQuery<UserConfig> {
            where(table.qq eq qq)
            select(table)
        }.fetchOneOrNull()
    }

    suspend fun findByTgOrQQ(id: Long): UserConfig? = withVT {
        createQuery<UserConfig> {
            where(or(table.tg eq id, table.qq eq id))
            select(table)
        }.fetchOneOrNull()
    }

    suspend fun findByTgOrQQ(tgIds: List<Long>, qqIds: List<Long>): List<UserConfig> = withVT {
        if (tgIds.isEmpty() && qqIds.isEmpty()) return@withVT emptyList()

        createQuery<UserConfig> {
            if (tgIds.isNotEmpty()) where(table.tg valueIn tgIds)
            if (qqIds.isNotEmpty()) where(table.qq valueIn qqIds)
            select(table)
        }.execute()
    }

    suspend fun findAll() = withVT {
        createQuery<UserConfig> {
            select(table)
        }.execute()
    }

}