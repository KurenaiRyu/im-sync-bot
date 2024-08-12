package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import kotlinx.serialization.json.Json
import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.domain.QQTg
import kurenai.imsyncbot.qqMessageRepository
import kurenai.imsyncbot.qqTgRepository
import kurenai.imsyncbot.utils.BotUtil.toEntity
import kurenai.imsyncbot.utils.BotUtil.toSource
import kurenai.imsyncbot.utils.getLogger
import kurenai.imsyncbot.utils.withIO
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.source
import net.mamoe.mirai.message.sourceMessage
import kotlin.jvm.optionals.getOrNull


object MessageService {

    private val log = getLogger()
    private val messageJson = Json {
        ignoreUnknownKeys = true
    }

    suspend fun save(message: QQMessage) = runCatching {
        withIO {
            qqMessageRepository.save(message)
        }
    }.onFailure {
        log.error("Save message failed", it)
    }

    /**
     * 缓存信息
     *
     * @param messageChain
     * @param messages
     */
    suspend fun cache(entity: QQMessage?, chain: MessageChain, messages: Array<TdApi.Message>? = null) =
        runCatching {
            withIO {
                val qqMsg = qqMessageRepository.save(
                    entity?.apply {
                        handled = true
                    } ?: chain.toEntity(handled = true)
                )
                messages?.map {
                    QQTg().apply {
                        this.qqId = qqMsg.id
                        this.qqMsgId = qqMsg.messageId
                        tgGrpId = it.chatId
                        tgMsgId = it.id
                    }
                }?.let(qqTgRepository::saveAll)
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
    suspend fun cache(receipt: MessageReceipt<*>, message: TdApi.Message) {
        cache(null, receipt.sourceMessage, arrayOf(message))
    }

    suspend fun findRelationByQuote(chain: MessageChain): QQTg? {
        // QuoteReply's source not contain target info
        return chain[QuoteReply.Key]?.let {
            val source = chain.source
            findRelationByQQ(source)
        }
    }

    suspend fun findRelationByQQ(source: MessageSource) =
        findRelationByQQ(source.botId, source.targetId, source.ids.first())

    suspend fun findRelationByQQ(botId: Long, targetId: Long, msgId: Int): QQTg? {
        require(botId > 0L) { "Bot id should be greater than to 0" }
        require(targetId > 0L) { "Target id should be greater than to 0" }
        require(msgId != 0) { "Message id should bot be 0" }
        return withIO {
            qqMessageRepository.findByBotIdAndTargetIdAndMessageId(botId, targetId, msgId)?.let {
                qqTgRepository.findByQqId(it.id)
            }
        }
    }

    suspend fun findQQMessageByDelete(update: TdApi.UpdateDeleteMessages): List<QQMessage> {
        return withIO {
            val qqIds =
                qqTgRepository.findByTgGrpIdAndTgMsgIdIn(update.chatId, update.messageIds.toList()).map { it.qqId }
            qqMessageRepository.findAllById(qqIds)
        }
    }

    suspend fun findRelationByRecall(event: MessageRecallEvent.GroupRecall): QQTg? {
        return findRelationByQQ(event.bot.id, event.group.id, event.messageIds[0])
    }

    suspend fun findQQMessageByTg(message: TdApi.Message) = findQQMessageByTg(message.chatId, message.id)

    suspend fun findQQMessageByTg(chatId: Long, messageId: Long): QQMessage? {
        return withIO {
            qqTgRepository.findByTgGrpIdAndTgMsgId(chatId, messageId)?.let {
                qqMessageRepository.findById(it.qqId).getOrNull()
            }
        }
    }

    suspend inline fun findQQByTg(message: TdApi.Message) = findQQByTg(message.chatId, message.id)

    suspend fun findQQByTg(chatId: Long, messageId: Long): MessageSource? {
        return withIO {
            qqTgRepository.findByTgGrpIdAndTgMsgId(chatId, messageId)?.let {
                qqMessageRepository.findById(it.qqId).getOrNull()
            }?.toSource()
        }
    }

}