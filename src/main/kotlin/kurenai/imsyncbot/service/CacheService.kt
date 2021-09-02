package kurenai.imsyncbot.service

import kurenai.imsyncbot.domain.MessageSourceCache
import mu.KotlinLogging
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.MessageSourceBuilder
import net.mamoe.mirai.message.data.OnlineMessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import java.util.concurrent.TimeUnit


@Component
class CacheService(
    private val cache: io.github.kurenairyu.cache.Cache,
) {
    companion object {
        const val TG_MSG_CACHE_KEY = "TG_MSG_CACHE"
        const val QQ_MSG_CACHE_KEY = "QQ_MSG_CACHE"
        const val TG_QQ_MSG_ID_CACHE_KEY = "TG_QQ_MSG_ID_CACHE"
        const val QQ_TG_MSG_ID_CACHE_KEY = "QQ_TG_MSG_ID_CACHE"
        val TTL = TimeUnit.DAYS.toMillis(7)
    }

    private val log = KotlinLogging.logger {}

    suspend fun cache(source: OnlineMessageSource, message: Message) {
        val qqMsgId: Int = source.ids[0]
        val tgMsgId: Int = message.messageId

        cache.put(QQ_TG_MSG_ID_CACHE_KEY, qqMsgId, tgMsgId, TTL)
        cache.put(TG_QQ_MSG_ID_CACHE_KEY, tgMsgId, qqMsgId, TTL)
        cache.put(QQ_MSG_CACHE_KEY, qqMsgId, MessageSourceCache(source), TTL)

        cache(message)
    }

    fun cache(message: Message) {
        cache.put(TG_MSG_CACHE_KEY, message.messageId, message, TTL)
    }

    fun getIdByTg(id: Int): Int? {
        return cache.get(TG_QQ_MSG_ID_CACHE_KEY, id)
    }

    fun getIdByQQ(id: Int): Int? {
        return cache.get(QQ_TG_MSG_ID_CACHE_KEY, id)
    }

    fun getQQ(id: Int): MessageSource? {
        return getOfflineQQ(id)
    }

    fun getOfflineQQ(id: Int): MessageSource? {
        val source: MessageSourceCache? = cache.get(QQ_MSG_CACHE_KEY, id)
        return if (source == null) {
            log.debug { "QQ source not found by $id" }
            null
        } else {
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
        return cache.get(TG_MSG_CACHE_KEY, id)
    }

    fun getByQQ(id: Int): Message? {
        return getIdByQQ(id)?.let {
            getTg(it)
        }
    }
}