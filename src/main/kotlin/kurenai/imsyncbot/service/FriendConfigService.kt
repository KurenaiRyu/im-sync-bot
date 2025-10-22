package kurenai.imsyncbot.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.asCache
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.domain.FriendConfig
import kurenai.imsyncbot.repository.FriendConfigRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object FriendConfigService {

    private val botCache = ConcurrentHashMap<Long, Cache>()

    context(bot: ImSyncBot)
    suspend fun findByQQ(id: Long): FriendConfig? {
        val cache = getCache()
        return cache.tg.get(id) {
            FriendConfigRepository.findByQQ(id)
        }?.also {
            cache.qq.put(it.telegramGroupId, it)
        }
    }

    context(bot: ImSyncBot)
    suspend fun findByTG(id: Long): FriendConfig? {
        val cache = getCache()
        return cache.qq.get(id) {
            FriendConfigRepository.findByQQ(id)
        }?.also {
            cache.tg.put(it.telegramGroupId, it)
        }
    }

    context(bot: ImSyncBot)
    private fun getCache(): Cache {
        return botCache.computeIfAbsent(bot.qq.qqBot.id) {
            buildCache()
        }
    }

    context(bot: ImSyncBot)
    private fun buildCache(): Cache {
        return Cache(
            bot.qq.qqBot.id,
            Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .maximumSize(50)
                .asCache(),
            Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .maximumSize(50)
                .asCache()
        )
    }

    private data class Cache(
        val botId: Long,
        val qq: com.sksamuel.aedile.core.Cache<Long, FriendConfig?>,
        val tg: com.sksamuel.aedile.core.Cache<Long, FriendConfig?>,
    )
}