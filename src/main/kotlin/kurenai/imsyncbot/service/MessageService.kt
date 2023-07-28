package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.domain.QQMessageType
import kurenai.imsyncbot.domain.QQTg
import kurenai.imsyncbot.domain.getLocalDateTime
import kurenai.imsyncbot.qqMessageRepository
import kurenai.imsyncbot.qqTgRepository
import kurenai.imsyncbot.utils.getLogger
import kurenai.imsyncbot.utils.withIO
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChain.Companion.serializeToJsonString
import net.mamoe.mirai.message.data.source
import net.mamoe.mirai.message.sourceMessage
import kotlin.jvm.optionals.getOrNull


object MessageService {

    private val log = getLogger()

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
    suspend fun cache(entity: QQMessage?, messageChain: MessageChain, messages: List<TdApi.Message>? = null) =
        runCatching {
            withIO {
                val qqMsg = qqMessageRepository.save(
                    entity?.apply {
                        handled = true
                    } ?: QQMessage().apply {
                        messageId = messageChain.source.ids[0]
                        botId = messageChain.source.botId
                        target = messageChain.source.targetId
                        sender = messageChain.source.fromId
                        type = QQMessageType.GROUP
                        json = messageChain.serializeToJsonString()
                        handled = true
                        msgTime = messageChain.source.getLocalDateTime()
                    }
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
        cache(null, receipt.sourceMessage.plus(receipt.source), listOf(message))
    }

    suspend fun findTgIdByQQ(botId: Long, targetId: Long, msgId: Int): QQTg? {
        return withIO {
            qqMessageRepository.findByBotIdAndObjIdAndMessageId(botId, targetId, msgId)?.let {
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
        return findTgIdByQQ(event.bot.id, event.group.id, event.messageIds[0])
    }

    suspend inline fun findQQByTg(message: TdApi.Message) = findQQByTg(message.chatId, message.id)

    suspend fun findQQByTg(chatId: Long, messageId: Long): MessageChain? {
        return withIO {
            qqTgRepository.findByTgGrpIdAndTgMsgId(chatId, messageId)?.let {
                qqMessageRepository.findById(it.qqId).getOrNull()
            }?.let {
                MessageChain.deserializeFromJsonString(it.json)
            }
        }
    }

}