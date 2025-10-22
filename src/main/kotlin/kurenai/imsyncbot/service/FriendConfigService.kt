package kurenai.imsyncbot.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.asCache
import kurenai.imsyncbot.domain.FriendConfig
import kurenai.imsyncbot.repository.FriendConfigRepository
import java.util.concurrent.TimeUnit

object FriendConfigService {

    private val tgCache = Caffeine.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .maximumSize(50)
        .asCache<Long, FriendConfig?>()

    private val qqCache = Caffeine.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .maximumSize(50)
        .asCache<Long, FriendConfig?>()

    suspend fun findByQQ(id: Long): FriendConfig? {
        return qqCache.get(id) {
            FriendConfigRepository.findByQQ(id)
        }?.also {
            tgCache.put(it.telegramGroupId, it)
        }
    }

    suspend fun findByTG(id: Long): FriendConfig? {
        return tgCache.get(id) {
            FriendConfigRepository.findByTG(id)
        }?.also {
            qqCache.put(it.qqId, it)
        }
    }

}