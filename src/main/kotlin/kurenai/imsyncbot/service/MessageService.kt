package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import kurenai.imsyncbot.domain.QQMessage
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
                messages?.map {
                    QQTg(qqMsg.id, qqMsg.messageId, it.chatId, it.id)
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