package kurenai.imsyncbot.service

import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.domain.QQTg
import kurenai.imsyncbot.domain.getLocalDateTime
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.qqMessageRepository
import kurenai.imsyncbot.qqTgRepository
import kurenai.imsyncbot.snowFlake
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.runIO
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.request.message.GetMessageInfo
import moe.kurenai.tdlight.util.getLogger
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChain.Companion.serializeToJsonString
import net.mamoe.mirai.message.data.source
import net.mamoe.mirai.message.sourceMessage
import org.redisson.api.RAtomicLong
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull


object CacheService {
    const val TG_MSG_CACHE_KEY = "TG_MSG_CACHE"
    const val QQ_TG_MSG_ID_CACHE_KEY = "QQ_TG_MSG_ID_CACHE"
    const val TG_FILE_CACHE_KEY = "TG_FILE_CACHE"
    const val TG_FILE_CACHE_TTL_KEY = "TG_FILE_CACHE_TTL"
    val TTL = TimeUnit.DAYS.toMillis(5)
    val BEGIN = LocalDateTime.of(2022, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.MIN)

    suspend fun hit(): RAtomicLong = getBotOrThrow().redisson.getAtomicLong(TG_FILE_CACHE_KEY.appendKey("HIT"))
    suspend fun total(): RAtomicLong = getBotOrThrow().redisson.getAtomicLong(TG_FILE_CACHE_KEY.appendKey("TOTAL"))

    private val log = getLogger()

    /**
     * 缓存信息
     *
     * @param messageChain
     * @param message
     */
    suspend fun cache(entity: QQMessage?, messageChain: MessageChain, message: Message) = runCatching {

        runIO {
            val qqMsg = qqMessageRepository.save(
                entity?.apply {
                    handled = true
                } ?: QQMessage(
                    messageChain.source.ids[0],
                    messageChain.source.botId,
                    messageChain.source.targetId,
                    messageChain.source.fromId,
                    messageChain.source.targetId,
                    QQMessage.QQMessageType.GROUP,
                    messageChain.serializeToJsonString(),
                    true,
                    messageChain.source.getLocalDateTime()
                )
            )

            qqTgRepository.save(
                QQTg(
                    qqMsg.id,
                    qqMsg.messageId,
                    message.chat.id,
                    message.messageId!!
                )
            )

            cache(message)
        }
    }.onFailure {
        log.error("Cache message failed", it)
    }

    /**
     * 缓存信息，用于receipt
     *
     *
     * @param receipt
     * @param message
     */
    suspend fun cache(receipt: MessageReceipt<*>, message: Message) {
        cache(null, receipt.sourceMessage.plus(receipt.source), message)
    }

    suspend fun cache(message: Message) {
        getBotOrThrow().cache.put(TG_MSG_CACHE_KEY, message.cacheId(), message, TTL)
    }

    suspend fun cacheFile(path: String) {
        val ttlSet = getBotOrThrow().redisson.getScoredSortedSet<String>(TG_FILE_CACHE_TTL_KEY)
        ttlSet.addScore(path, LocalDateTime.now().plusHours(1).durationSeconds())
    }

    suspend fun cacheFile(path: Path) {
        cacheFile(path.pathString)
    }

    suspend fun cacheImg(image: Path) {
        cacheFile(image)
    }

    suspend fun getTgIdByQQ(group: Long, id: Int): Pair<Long, Long>? {
        return getBotOrThrow().cache.get<String, String?>(QQ_TG_MSG_ID_CACHE_KEY, getQQCacheId(group, id))
            ?.splitCacheId()
    }

    suspend inline fun getQQByTg(message: Message) = getQQByTg(message.chat.id, message.messageId!!)

    suspend fun getQQByTg(chatId: Long, messageId: Long): MessageChain? {
        return runIO {
            qqTgRepository.findByTgMsgIdAndTgGrpId(messageId, chatId)?.let {
                qqMessageRepository.findById(it.qqId).getOrNull()
            }?.let {
                MessageChain.deserializeFromJsonString(it.json)
            }
        }
    }

    suspend fun getTg(chatId: Long, messageId: Long): Message? {
        return getBotOrThrow().cache.get(TG_MSG_CACHE_KEY, getTgCacheId(chatId, messageId))
            ?: getOnlineTg(chatId.toString(), messageId)
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