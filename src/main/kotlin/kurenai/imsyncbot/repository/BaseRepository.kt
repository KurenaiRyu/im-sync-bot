package kurenai.imsyncbot.repository

import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.withIO
import kotlin.reflect.KClass

open class BaseRepository<T: Any, ID: Any> {

    suspend inline fun <reified R: T> findById(id: ID) = withIO {
        sqlClient.findById(R::class, id)
    }

    suspend inline fun <reified R: T> findByIds(ids: List<ID>) = withIO {
        sqlClient.findByIds(R::class, ids)
    }

    suspend fun save(item: T) = withIO {
        sqlClient.save(item).modifiedEntity
    }

    suspend fun saveAll(items: List<T>) = withIO {
        items.map { item ->
            sqlClient.save(item).isModified
        }.count { it }
    }

}