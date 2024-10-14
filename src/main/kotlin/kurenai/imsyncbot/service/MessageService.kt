package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import kurenai.imsyncbot.domain.*
import kurenai.imsyncbot.imSyncBot
import kurenai.imsyncbot.qqTgRepository
import kurenai.imsyncbot.repository.QQMessageRepository
import kurenai.imsyncbot.repository.QQTgRepository
import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.BotUtil.toEntity
import kurenai.imsyncbot.utils.getLogger
import kurenai.imsyncbot.utils.withIO
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.sourceMessage
import org.babyfish.jimmer.kt.new
import top.mrxiaom.overflow.Overflow


object MessageService {

    private val log = getLogger()

    suspend fun save(message: QQMessage) = withIO {
        sqlClient.save(message).modifiedEntity
    }

    /**
     * 缓存信息
     *
     * @param messageChain
     * @param messages
     */
    suspend fun cache(chain: MessageChain, messages: Array<TdApi.Message>? = null) = runCatching {
        withIO {
            val qqMsg = QQMessageRepository.findByChain(chain)?.let { entity ->
                save(entity.copy {
                    handled = true
                })
            } ?: chain.toEntity(true)

            messages?.map {
                new(QQTg::class).by {
                    this.qqId = qqMsg.id
                    this.qqMsgId = qqMsg.messageId
                    tgGrpId = it.chatId
                    tgMsgId = it.id
                }
            }?.let {
                QQTgRepository.saveAll(it)
            }
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
        cache(receipt.sourceMessage, arrayOf(message))
    }

    suspend fun findRelationByQuote(chain: MessageChain): QQTg? {
        // QuoteReply's source not contain target info
        return chain[QuoteReply.Key]?.let {
            findRelationByQQ(chain.source.botId, chain.source.targetId, it.source.ids.first())
        }
    }

    suspend fun findRelationByQQ(source: MessageSource) =
        findRelationByQQ(source.botId, source.targetId, source.ids.first())

    suspend fun findRelationByQQ(botId: Long, targetId: Long, msgId: Int): QQTg? {
        require(botId > 0L) { "Bot id should be greater than to 0" }
        require(targetId > 0L) { "Target id should be greater than to 0" }
        require(msgId != 0) { "Message id should bot be 0" }
        return QQMessageRepository.findByBotIdAndTargetIdAndMessageId(botId, targetId, msgId)?.let {
            qqTgRepository.findByQqId(it.id).firstOrNull()
        }
    }

    suspend fun findQQMessageByDelete(update: TdApi.UpdateDeleteMessages): List<QQMessage> {
        val qqIds =
            qqTgRepository.findByTgGrpIdAndTgMsgIdIn(update.chatId, update.messageIds.toList()).map { it.qqId }
        return QQMessageRepository.findByIds(qqIds)
    }

    suspend fun findRelationByRecall(event: MessageRecallEvent.GroupRecall): QQTg? {
        return findRelationByQQ(event.bot.id, event.group.id, event.messageIds[0])
    }

    suspend fun findQQMessageByTg(message: TdApi.Message) = findQQMessageByTg(message.chatId, message.id)

    suspend fun findQQMessageByTg(chatId: Long, messageId: Long): QQMessage? {
        return qqTgRepository.findOneByTgGrpIdAndTgMsgId(chatId, messageId)?.let {
            QQMessageRepository.findById(it.qqId)
        }
    }

    suspend inline fun findQQByTg(message: TdApi.Message) = findQQByTg(message.chatId, message.id)

    suspend fun findQQByTg(chatId: Long, messageId: Long): MessageChain? {
        return qqTgRepository.findOneByTgGrpIdAndTgMsgId(chatId, messageId)?.let {
            QQMessageRepository.findById<QQMessage>(it.qqId)
        }?.let {
            Overflow.deserializeMessage(imSyncBot.qq.qqBot, it.json)
        }
    }

}