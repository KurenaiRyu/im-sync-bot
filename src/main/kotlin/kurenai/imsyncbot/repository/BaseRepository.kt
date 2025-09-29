package kurenai.imsyncbot.repository

import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.withVT
import org.babyfish.jimmer.sql.kt.ast.query.KConfigurableRootQuery
import org.babyfish.jimmer.sql.kt.ast.query.KMutableRootQuery
import org.babyfish.jimmer.sql.kt.ast.table.KNonNullTable

open class BaseRepository<T : Any, ID : Any> {

    suspend inline fun <reified R : T> findById(id: ID) = withVT {
        sqlClient.findById(R::class, id)
    }

    suspend inline fun <reified R : T> findByIds(ids: List<ID>) = withVT {
        sqlClient.findByIds(R::class, ids)
    }

    suspend fun save(item: T) = withVT {
        sqlClient.save(item).modifiedEntity
    }

    suspend fun saveAll(items: List<T>) = withVT {
        items.map { item ->
            sqlClient.save(item).isModified
        }.count { it }
    }

    suspend inline fun <reified R : T> deleteById(id: ID) = withVT {
        sqlClient.deleteById(R::class, id).totalAffectedRowCount
    }

    suspend inline fun <reified R : T> deleteByIds(ids: List<ID>) = withVT {
        sqlClient.deleteByIds(R::class, ids).totalAffectedRowCount
    }

    protected inline fun <reified C : T, reified R> createQuery(
        noinline block: KMutableRootQuery.ForEntity<C>.() -> KConfigurableRootQuery<KNonNullTable<C>, R>
    ): KConfigurableRootQuery<KNonNullTable<C>, R> = sqlClient.createQuery(entityType = C::class, block = block)

}