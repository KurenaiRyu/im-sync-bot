package kurenai.imsyncbot.service

import io.ktor.http.*
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.entity.FileCache
import kurenai.imsyncbot.entity.MessageSourceCache
import mu.KotlinLogging
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.MessageSourceBuilder
import net.mamoe.mirai.message.data.OnlineMessageSource
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.GetMessageInfo
import org.telegram.telegrambots.meta.api.objects.Message
import java.io.File
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
        val qqMsgId: Int = source.ids[0]
        val tgMsgId: String = message.cacheId()

        cache.put(QQ_TG_MSG_ID_CACHE_KEY, qqMsgId, tgMsgId, TTL)
        cache.put(TG_QQ_MSG_ID_CACHE_KEY, tgMsgId, qqMsgId, TTL)
        cache.put(QQ_MSG_CACHE_KEY, qqMsgId, MessageSourceCache(source), TTL)

        cache(message)
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
        cache.put(TG_IMG_CACHE_KEY, image.name.encodeURLPath(), count + 1, IMAGE_TTL)
    }

    fun imgExists(name: String): Boolean {
        return cache.exists(TG_IMG_CACHE_KEY, name.encodeURLPath())
    }

    fun getFile(qqId: String): FileCache? {
        return cache.get(TG_FILE_CACHE_KEY, qqId)
    }

    fun cache(message: Message) {
        cache.put(TG_MSG_CACHE_KEY, message.cacheId(), message, TTL)
    }

    fun getQQIdByTg(message: Message): Int? {
        return getQQIdByTg(message.chatId, message.messageId)
    }

    fun getQQIdByTg(chatId: Long, messageId: Int): Int? {
        return cache.get(TG_QQ_MSG_ID_CACHE_KEY, getTgCacheId(chatId, messageId))
    }

    fun getTelegramIdByQQ(id: Int): TelegramId? {
        return cache.get<Int, String?>(QQ_TG_MSG_ID_CACHE_KEY, id)?.let { cacheId ->
            val split = cacheId.split("/")
            return if (split.size != 2) null
            else TelegramId(split[0].toLong(), split[1].toInt())
        }
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

    fun getQQByTg(message: Message): MessageSource? {
        val msgId = getQQIdByTg(message.chatId, message.messageId)
        return if (msgId == null) {
            log.debug { "QQ msg id not found by ${message.chatId}/${message.messageId}" }
            null
        } else {
            getQQ(msgId)
        }
    }

    fun getQQByTg(chatId: Long, messageId: Int): MessageSource? {
        val msgId = getQQIdByTg(chatId, messageId)
        return if (msgId == null) {
            log.debug { "QQ msg id not found by $msgId" }
            null
        } else {
            getQQ(msgId)
        }
    }

    fun getTg(message: Message): Message? {
        return getTg(message.chatId, message.messageId)
    }

    fun getTg(chatId: Long, messageId: Int): Message? {
        return cache.get(TG_MSG_CACHE_KEY, getTgCacheId(chatId, messageId)) ?: getOnlineTg(chatId.toString(), messageId)
    }

    fun getOnlineTg(chatId: String?, messageId: Int): Message? {
        if (chatId == null) return null
        return ContextHolder.telegramBotClient.send(GetMessageInfo(chatId, messageId))?.also {
            cache(it)
        }
    }

    fun getTgByQQ(group: Long, id: Int): Message? {
        return getTelegramIdByQQ(id)?.let { telegramId ->
            getTg(telegramId.chatId, telegramId.messageId)
        }
    }

    fun getPrivateChannelMessageId(friendId: Long): Int? {
        return cache.get(QQ_TG_PRIVATE_MSG_ID_CACHE_KEY, friendId)
    }

    fun getFriendId(messageId: Int): Long? {
        return cache.get(TG_QQ_PRIVATE_MSG_ID_CACHE_KEY, messageId)
    }

    private fun getTgCacheId(chatId: Long, messageId: Int): String {
        return "${chatId}/${messageId}"
    }

}

data class TelegramId(
    val chatId: Long,
    val messageId: Int
)

fun Message.cacheId(): String {
    return "${chatId}/${messageId}"
}