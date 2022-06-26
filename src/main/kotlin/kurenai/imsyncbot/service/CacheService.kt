package kurenai.imsyncbot.service

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.ContextHolder.redisson
import kurenai.imsyncbot.entity.FileCache
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.request.message.GetMessageInfo
import mu.KotlinLogging
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChain.Companion.serializeToJsonString
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.ids
import net.mamoe.mirai.message.data.source
import net.mamoe.mirai.message.sourceMessage
import org.redisson.api.RAtomicLong
import org.redisson.client.protocol.ScoredEntry
import java.io.File
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


class CacheService {
    companion object {
        const val TG_MSG_CACHE_KEY = "TG_MSG_CACHE"
        const val QQ_MSG_CACHE_KEY = "QQ_MSG_CACHE"
        const val TG_QQ_MSG_ID_CACHE_KEY = "TG_QQ_MSG_ID_CACHE"
        const val QQ_TG_MSG_ID_CACHE_KEY = "QQ_TG_MSG_ID_CACHE"
        const val TG_FILE_CACHE_KEY = "TG_FILE_CACHE"
        const val TG_FILE_CACHE_TTL_KEY = "TG_FILE_CACHE_TTL"
        const val TG_IMG_CACHE_KEY = "TG_IMG_CACHE"
        const val QQ_TG_PRIVATE_MSG_ID_CACHE_KEY = "QQ_TG_PRIVATE_MSG_ID_CACHE"
        const val TG_QQ_PRIVATE_MSG_ID_CACHE_KEY = "TG_QQ_PRIVATE_MSG_ID_CACHE"
        val TTL = TimeUnit.DAYS.toMillis(5)
        val BEGIN = LocalDateTime.of(2022, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.MIN)
    }

    val cache = ContextHolder.cache
    val hit: RAtomicLong = redisson.getAtomicLong(TG_FILE_CACHE_KEY.appendKey("HIT"))
    val total: RAtomicLong = redisson.getAtomicLong(TG_FILE_CACHE_KEY.appendKey("TOTAL"))

    private val log = KotlinLogging.logger {}

    fun cache(messageChain: MessageChain, message: Message) {
        val qqMsgId: String = messageChain.cacheId()
        val tgMsgId: String = message.cacheId()

        cache.put(QQ_TG_MSG_ID_CACHE_KEY, qqMsgId, tgMsgId, TTL)
        cache.put(TG_QQ_MSG_ID_CACHE_KEY, tgMsgId, qqMsgId, TTL)
        cache.put(QQ_MSG_CACHE_KEY, qqMsgId, messageChain.serializeToJsonString(), TTL)
        cache(message)
    }

    fun cache(receipt: MessageReceipt<*>, message: Message) {
        cache(receipt.sourceMessage.plus(receipt.source), message)
    }

    fun cache(message: Message) {
        cache.put(TG_MSG_CACHE_KEY, message.cacheId(), message, TTL)
    }

    fun cachePrivateChat(friendId: Long, messageId: Int) {
        cache.put(TG_QQ_PRIVATE_MSG_ID_CACHE_KEY, messageId, friendId)
        cache.put(QQ_TG_PRIVATE_MSG_ID_CACHE_KEY, friendId, messageId)
    }

    fun cacheFile(qqFileId: String, fileCache: FileCache) {
        cache.put(TG_FILE_CACHE_KEY, qqFileId, fileCache, TTL)
    }

    fun cacheFile(file: File) {
        val ttlSet = redisson.getScoredSortedSet<String>(TG_FILE_CACHE_TTL_KEY)
        ttlSet.addScore(file.path, LocalDateTime.now().plusHours(1).durationSeconds())
    }

    fun cacheImg(image: File) {
        cacheFile(image)
    }

    fun getNotExistFiles(): MutableCollection<ScoredEntry<String>>? {
        val ttlSet = redisson.getScoredSortedSet<String>(TG_FILE_CACHE_TTL_KEY)
        val now = getNowSeconds().toDouble()
        //TODO atomic by script
        val entries = ttlSet.entryRange(0.0, true, now, false)
        ttlSet.removeRangeByScore(0.0, true, now, false)
        return entries
    }

    fun getFile(qqFileId: String): FileCache? {
        return cache.get<String?, FileCache?>(TG_FILE_CACHE_KEY, qqFileId).also {
            val t = total.incrementAndGet()
            if (it != null) {
                val h = hit.incrementAndGet()
                val formatter = NumberFormat.getPercentInstance()
                formatter.maximumFractionDigits = 2

                log.info { "Cache hit: $h / $t (${formatter.format(h / t.toFloat())})" }
            } else {
                hit.get()
            }
        }
    }

    fun getQQIdByTg(message: Message): Pair<Long, Int>? {
        return getQQIdByTg(message.chat.id, message.messageId!!)
    }

    fun getQQIdByTg(chatId: Long, messageId: Int): Pair<Long, Int>? {
        return cache.get<String, String?>(TG_QQ_MSG_ID_CACHE_KEY, getTgCacheId(chatId, messageId))?.splitCacheId()
    }

    fun getTgIdByQQ(group: Long, id: Int): Pair<Long, Int>? {
        return cache.get<String, String?>(QQ_TG_MSG_ID_CACHE_KEY, getQQCacheId(group, id))?.splitCacheId()
    }

    fun getQQ(group: Long, id: Int): MessageChain? {
        return getOfflineQQ(group, id)
    }

    fun getOfflineQQ(group: Long, id: Int): MessageChain? {
        val json: String? = cache.get(QQ_MSG_CACHE_KEY, getQQCacheId(group, id))
        return if (json == null) {
            log.debug { "QQ source not found by $group:$id" }
            null
        } else {
            try {
                MessageChain.deserializeFromJsonString(json)
            } catch (e: Exception) {
                log.error("Deserialize error: $json")
                null
            }
        }
    }

    fun getQQByTg(message: Message): MessageChain? {
        val msgId = getQQIdByTg(message.chat.id, message.messageId!!)
        return if (msgId == null) {
            log.debug { "QQ msg id not found by ${message.cacheId()}" }
            null
        } else {
            getQQ(msgId.first, msgId.second)
        }
    }

    fun getQQByTg(chatId: Long, messageId: Int): MessageChain? {
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

    private fun MessageChain.cacheId(): String {
        return getQQCacheId(source.targetId, ids[0])
    }

    private fun MessageSource.cacheId(): String {
        return getQQCacheId(targetId, ids[0])
    }

    private fun Double.isExist(): Boolean {
        return this - getNowSeconds() > 0
    }

    private fun getNowSeconds(): Long {
        return LocalDateTime.now().toEpochSecond(ZoneOffset.MIN) - BEGIN
    }

    private fun LocalDateTime.durationSeconds(): Long {
        return this.toEpochSecond(ZoneOffset.MIN) - BEGIN
    }

    private fun String.splitCacheId(): Pair<Long, Int>? {
        return this.split(":").takeIf { it.size == 2 }?.let { it[0].toLong() to it[1].toInt() }
    }

    private fun String.appendKey(key: String): String {
        return "$this:$key"
    }

}