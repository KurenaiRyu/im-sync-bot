package kurenai.imsyncbot.service

import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.domain.FileCache
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.request.message.GetMessageInfo
import moe.kurenai.tdlight.util.getLogger
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
import java.nio.file.Path
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString


object CacheService {
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

    suspend fun hit(): RAtomicLong = getBotOrThrow().redisson.getAtomicLong(TG_FILE_CACHE_KEY.appendKey("HIT"))
    suspend fun total(): RAtomicLong = getBotOrThrow().redisson.getAtomicLong(TG_FILE_CACHE_KEY.appendKey("TOTAL"))

    private val log = getLogger()

    /**
     * 缓存信息，不适用从Receipt中拿到的chain
     *
     *
     * @param messageChain
     * @param message
     */
    @Suppress("NAME_SHADOWING")
    suspend fun cache(messageChain: MessageChain, message: Message, bot: ImSyncBot? = null) {
        val bot = bot ?: getBotOrThrow()
        val qqMsgId: String = messageChain.cacheId()
        val tgMsgId: String = message.cacheId()

        bot.cache.put(QQ_TG_MSG_ID_CACHE_KEY, qqMsgId, tgMsgId, TTL)
        bot.cache.put(TG_QQ_MSG_ID_CACHE_KEY, tgMsgId, qqMsgId, TTL)
        bot.cache.put(QQ_MSG_CACHE_KEY, qqMsgId, messageChain.serializeToJsonString(), TTL)
        cache(message)
    }

    /**
     * 缓存信息，用于receipt
     *
     *
     * @param receipt
     * @param message
     */
    suspend fun cache(receipt: MessageReceipt<*>, message: Message) {
        cache(receipt.sourceMessage.plus(receipt.source), message)
    }

    suspend fun cache(message: Message) {
        getBotOrThrow().cache.put(TG_MSG_CACHE_KEY, message.cacheId(), message, TTL)
    }

    suspend fun cachePrivateChat(friendId: Long, messageId: Long) {
        val bot = getBotOrThrow()
        bot.cache.put(TG_QQ_PRIVATE_MSG_ID_CACHE_KEY, messageId, friendId)
        bot.cache.put(QQ_TG_PRIVATE_MSG_ID_CACHE_KEY, friendId, messageId)
    }

    suspend fun cacheFile(qqFileId: String, fileCache: FileCache) {
        getBotOrThrow().cache.put(TG_FILE_CACHE_KEY, qqFileId, fileCache, TTL)
    }

    suspend fun cacheFile(file: File) {
        cacheFile(file.path)
    }

    suspend fun cacheFile(path: String) {
        val ttlSet = getBotOrThrow().redisson.getScoredSortedSet<String>(TG_FILE_CACHE_TTL_KEY)
        ttlSet.addScore(path, LocalDateTime.now().plusHours(1).durationSeconds())
    }

    suspend fun cacheImg(image: File) {
        cacheFile(image)
    }

    suspend fun cacheFile(path: Path) {
        cacheFile(path.pathString)
    }

    suspend fun cacheImg(image: Path) {
        cacheFile(image)
    }

    suspend fun getNotExistFiles(): MutableCollection<ScoredEntry<String>>? {
        val ttlSet = getBotOrThrow().redisson.getScoredSortedSet<String>(TG_FILE_CACHE_TTL_KEY)
        val now = getNowSeconds().toDouble()
        //TODO atomic by script
        val entries = ttlSet.entryRange(0.0, true, now, false)
        ttlSet.removeRangeByScore(0.0, true, now, false)
        return entries
    }

    suspend fun getFile(qqFileId: String): FileCache? {
        return getBotOrThrow().cache.get<String?, FileCache?>(TG_FILE_CACHE_KEY, qqFileId).also {
            val t = total().incrementAndGet()
            if (it != null) {
                val h = hit().incrementAndGet()
                val formatter = NumberFormat.getPercentInstance()
                formatter.maximumFractionDigits = 2

                log.info("Cache hit: $h / $t (${formatter.format(h / t.toFloat())})")
            } else {
                hit().get()
            }
        }
    }

    suspend fun getQQIdByTg(message: Message): Pair<Long, Int>? {
        return getQQIdByTg(message.chat.id, message.messageId!!)
    }

    suspend fun getQQIdByTg(chatId: Long, messageId: Long): Pair<Long, Int>? {
        return getBotOrThrow().cache.get<String, String?>(TG_QQ_MSG_ID_CACHE_KEY, getTgCacheId(chatId, messageId))?.splitCacheId()?.let {
            it.first to it.second.toInt()
        }
    }

    suspend fun getTgIdByQQ(group: Long, id: Int): Pair<Long, Long>? {
        return getBotOrThrow().cache.get<String, String?>(QQ_TG_MSG_ID_CACHE_KEY, getQQCacheId(group, id))?.splitCacheId()
    }

    suspend fun getQQ(group: Long, id: Int): MessageChain? {
        return getOfflineQQ(group, id)
    }

    suspend fun getOfflineQQ(group: Long, id: Int): MessageChain? {
        val json: String? = getBotOrThrow().cache.get(QQ_MSG_CACHE_KEY, getQQCacheId(group, id))
        return if (json == null) {
            log.debug("QQ source not found by $group:$id")
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

    suspend fun getQQByTg(message: Message): MessageChain? {
        val msgId = getQQIdByTg(message.chat.id, message.messageId!!)
        return if (msgId == null) {
            log.debug("QQ msg id not found by ${message.cacheId()}")
            null
        } else {
            getQQ(msgId.first, msgId.second)
        }
    }

    suspend fun getQQByTg(chatId: Long, messageId: Long): MessageChain? {
        val msgId = getQQIdByTg(chatId, messageId)
        return if (msgId == null) {
            log.debug("QQ msg id not found by $msgId")
            null
        } else {
            getQQ(msgId.first, msgId.second)
        }
    }

    suspend fun getTg(chatId: Long, messageId: Long): Message? {
        return getBotOrThrow().cache.get(TG_MSG_CACHE_KEY, getTgCacheId(chatId, messageId)) ?: getOnlineTg(chatId.toString(), messageId)
    }

    suspend fun getOnlineTg(chatId: String?, messageId: Long): Message? {
        if (chatId == null) return null
        return GetMessageInfo(chatId, messageId).send().also {
            cache(it)
        }
    }

    suspend fun getTgByQQ(group: Long, id: Int): Message? {
        return getTgIdByQQ(group, id)?.let { pair ->
            getTg(pair.first, pair.second)
        }
    }

    private fun getTgCacheId(chatId: Long, messageId: Long): String {
        return "${chatId}:${messageId}"
    }

    private fun getQQCacheId(target: Long, id: Int): String {
        return "${target}:${id}"
    }

    private fun Message.cacheId(): String {
        return getTgCacheId(chat.id, messageId!!)
    }

    private fun MessageChain.cacheId(): String {
        return getQQCacheId(
            source.targetId,
            ids.firstOrNull() ?: throw BotException("回执消息ids为空，消息可能没有发出，也许是风控了。")
        )
    }

    private fun getNowSeconds(): Long {
        return LocalDateTime.now().toEpochSecond(ZoneOffset.MIN) - BEGIN
    }

    private fun LocalDateTime.durationSeconds(): Long {
        return this.toEpochSecond(ZoneOffset.MIN) - BEGIN
    }

    private fun String.splitCacheId(): Pair<Long, Long>? {
        return this.split(":").takeIf { it.size == 2 }?.let { it[0].toLong() to it[1].toLong() }
    }

    private fun String.appendKey(key: String): String {
        return "$this:$key"
    }

}