package kurenai.mybot.service

import kurenai.mybot.cache.Cache
import kurenai.mybot.domain.MessageSourceCache
import mu.KotlinLogging
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.MessageSourceBuilder
import net.mamoe.mirai.message.data.OnlineMessageSource
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import java.util.concurrent.TimeUnit


@Component
class CacheService(
    private val redisTemplate: RedisTemplate<String, Any>,
) {
    companion object {
        const val TG_MSG_CACHE_KEY = "TG_MSG_CACHE"
        const val QQ_MSG_CACHE_KEY = "QQ_MSG_CACHE"
        const val TG_QQ_MSG_ID_CACHE_KEY = "TG_QQ_MSG_ID_CACHE"
        const val QQ_TG_MSG_ID_CACHE_KEY = "QQ_TG_MSG_ID_CACHE"
    }

    private val log = KotlinLogging.logger {}

    val qqMsgCache = Cache<Int, OnlineMessageSource>("QQ_MSG_CACHE", 40000)
    private val ops = redisTemplate.opsForValue()


    suspend fun cache(source: OnlineMessageSource, message: Message) {
        val qqMsgId = source.ids[0]
        val tgMsgId = message.messageId

        qqMsgCache.put(qqMsgId, source, 3, TimeUnit.DAYS)

        "$QQ_TG_MSG_ID_CACHE_KEY:$qqMsgId".let {
            ops.set(it, tgMsgId)
            redisTemplate.expire(it, 7, TimeUnit.DAYS)
        }
        "${TG_QQ_MSG_ID_CACHE_KEY}:$tgMsgId".let {
            ops.set(it, qqMsgId)
            redisTemplate.expire(it, 7, TimeUnit.DAYS)
        }
        "$QQ_MSG_CACHE_KEY:$qqMsgId".let {
            ops.set(it, MessageSourceCache(source))
            redisTemplate.expire(it, 7, TimeUnit.DAYS)
        }

        cache(message)
    }

    fun cache(message: Message) {
        "$TG_MSG_CACHE_KEY:${message.messageId}".let {
            ops.set(it, message)
            redisTemplate.expire(it, 7, TimeUnit.DAYS)
        }
    }

    fun getIdByTg(id: Int): Int? {
        return ops.get("$TG_QQ_MSG_ID_CACHE_KEY:$id")?.let { it as Int }
    }

    fun getIdByQQ(id: Int): Int? {
        return ops.get("$QQ_TG_MSG_ID_CACHE_KEY:$id")?.let { it as Int }
    }

    fun getQQ(id: Int): MessageSource? {
        return qqMsgCache[id] ?: getOfflineQQ(id)
    }

    fun getOfflineQQ(id: Int): MessageSource? {
        val source = ops.get("$QQ_MSG_CACHE_KEY:$id")
        return if (source == null) {
            log.debug { "QQ source not found by $id" }
            null
        } else {
            source as MessageSourceCache
            MessageSourceBuilder().apply {
                this.ids = source.ids
                this.time = source.time
                this.fromId = source.fromId
                this.targetId = source.targetId
                this.internalIds = source.internalIds
                this.messages { this.add(source.content) }
            }.build(source.botId, source.kind)
        }
    }

    fun getByTg(id: Int): MessageSource? {
        val msgId = getIdByTg(id)
        return if (msgId == null) {
            log.debug { "QQ msg id not found by $id" }
            null
        } else {
            getQQ(msgId)
        }
    }

    fun getTg(id: Int): Message? {
        return ops.get("$TG_MSG_CACHE_KEY:$id")?.let { it as Message }
    }

    fun getByQQ(id: Int): Message? {
        return getIdByQQ(id)?.let {
            getTg(it)
        }
    }
}