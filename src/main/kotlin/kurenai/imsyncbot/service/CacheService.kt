package kurenai.imsyncbot.service

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.entity.FileCache
import kurenai.imsyncbot.entity.MessageSourceCache
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.request.message.GetMessageInfo
import mu.KotlinLogging
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.MessageSourceBuilder
import net.mamoe.mirai.message.data.OnlineMessageSource
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


@Service
class CacheService(
    private val cache: io.github.kurenairyu.cache.Cache,
) : InitializingBean {
    companion object {
        const val TG_MSG_CACHE_KEY = "TG_MSG_CACHE"
        const val QQ_MSG_CACHE_KEY = "QQ_MSG_CACHE"
        const val TG_QQ_MSG_ID_CACHE_KEY = "TG_QQ_MSG_ID_CACHE"
        const val QQ_TG_MSG_ID_CACHE_KEY = "QQ_TG_MSG_ID_CACHE"
        const val TG_FILE_CACHE_KEY = "TG_FILE_CACHE"
        const val TG_IMG_CACHE_KEY = "TG_IMG_CACHE"
        const val QQ_TG_PRIVATE_MSG_ID_CACHE_KEY = "QQ_TG_PRIVATE_MSG_ID_CACHE"
        const val TG_QQ_PRIVATE_MSG_ID_CACHE_KEY = "TG_QQ_PRIVATE_MSG_ID_CACHE"
        val TTL = TimeUnit.DAYS.toMillis(5)
        val IMAGE_TTL = TimeUnit.DAYS.toMillis(1)
    }

    private val log = KotlinLogging.logger {}

    override fun afterPropertiesSet() {
        ContextHolder.cacheService = this
    }

    fun cache(source: OnlineMessageSource, message: Message) {
        if (source.ids.isEmpty()) {
            log.warn { "source ids is empty: $source" }
            return
        }
        val qqMsgId: String = source.cacheId()
        val tgMsgId: String = message.cacheId()

        cache.put(QQ_TG_MSG_ID_CACHE_KEY, qqMsgId, tgMsgId, TTL)
        cache.put(TG_QQ_MSG_ID_CACHE_KEY, tgMsgId, qqMsgId, TTL)
        cache.put(QQ_MSG_CACHE_KEY, qqMsgId, MessageSourceCache(source), TTL)

        cache(message)
    }

    fun cache(message: Message) {
        cache.put(TG_MSG_CACHE_KEY, message.cacheId(), message, TTL)
    }

    fun cachePrivateChat(friendId: Long, messageId: Int) {
        cache.put(TG_QQ_PRIVATE_MSG_ID_CACHE_KEY, messageId, friendId)
        cache.put(QQ_TG_PRIVATE_MSG_ID_CACHE_KEY, friendId, messageId)
    }

    fun cacheFile(qqId: String, fileCache: FileCache) {
        cache.put(TG_FILE_CACHE_KEY, qqId, fileCache, TTL)
    }

    fun cacheImg(image: File) {
        val count: Int = cache.get(TG_IMG_CACHE_KEY, image.name) ?: 0
        cache.put(TG_IMG_CACHE_KEY, image.name, count + 1, IMAGE_TTL)
    }

    fun imgExists(name: String): Boolean {
        return cache.exists(TG_IMG_CACHE_KEY, name)
    }

    fun getFile(qqId: String): FileCache? {
        return cache.get(TG_FILE_CACHE_KEY, qqId)
    }

    fun getQQIdByTg(message: Message): Pair<Long, Int>? {
        return getQQIdByTg(message.chat.id, message.messageId!!)
    }

    fun getQQIdByTg(chatId: Long, messageId: Int): Pair<Long, Int>? {
        return cache.get<String, String?>(TG_QQ_MSG_ID_CACHE_KEY, getTgCacheId(chatId, messageId))?.splitCacheId()
    }

    fun getTgIdByQQ(group: Long, id: Int): Pair<Long, Int>? {
        return cache.get<Int, String?>(QQ_TG_MSG_ID_CACHE_KEY, id)?.splitCacheId()
    }

    fun getQQ(group: Long, id: Int): MessageSource? {
        return getOfflineQQ(group, id)
    }

    fun getOfflineQQ(group: Long, id: Int): MessageSource? {
        val source: MessageSourceCache? = cache.get(QQ_MSG_CACHE_KEY, getQQCacheId(group, id))
        return if (source == null) {
            log.debug { "QQ source not found by $group:$id" }
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

    fun getQQByTg(message: Message): MessageSource? {
        val msgId = getQQIdByTg(message.chat.id, message.messageId!!)
        return if (msgId == null) {
            log.debug { "QQ msg id not found by ${message.cacheId()}" }
            null
        } else {
            getQQ(msgId.first, msgId.second)
        }
    }

    fun getQQByTg(chatId: Long, messageId: Int): MessageSource? {
        val msgId = getQQIdByTg(chatId, messageId)
        return if (msgId == null) {
            log.debug { "QQ msg id not found by $msgId" }
            null
        } else {
            getQQ(msgId.first, msgId.second)
        }
    }

    fun getTg(message: Message): Message? {
        return getTg(message.chat.id, message.messageId!!)
    }

    fun getTg(chatId: Long, messageId: Int): Message? {
        return cache.get(TG_MSG_CACHE_KEY, getTgCacheId(chatId, messageId)) ?: getOnlineTg(chatId.toString(), messageId)?.join()
    }

    fun getOnlineTg(chatId: String?, messageId: Int): CompletableFuture<Message>? {
        if (chatId == null) return null
        return GetMessageInfo(chatId, messageId).send().thenApply {
            cache(it)
            return@thenApply it
        }
    }

    fun getTgByQQ(group: Long, id: Int): Message? {
        return getTgIdByQQ(group, id)?.let { pair ->
            getTg(pair.first, pair.second)
        }
    }

    fun getPrivateChannelMessageId(friendId: Long): Int? {
        return cache.get(QQ_TG_PRIVATE_MSG_ID_CACHE_KEY, friendId)
    }

    fun getFriendId(messageId: Int): Long? {
        return cache.get(TG_QQ_PRIVATE_MSG_ID_CACHE_KEY, messageId)
    }

    private fun getTgCacheId(chatId: Long, messageId: Int): String {
        return "${chatId}:${messageId}"
    }

    private fun getQQCacheId(target: Long, id: Int): String {
        return "${target}:${id}"
    }

    private fun Message.cacheId(): String {
        return getTgCacheId(chat.id, messageId!!)
    }

    private fun OnlineMessageSource.cacheId(): String {
        return getQQCacheId(targetId, ids[0])
    }

    private fun String.splitCacheId(): Pair<Long, Int>? {
        return this.split(":").takeIf { it.size == 2 }?.let { it[0].toLong() to it[1].toInt() }
    }

}