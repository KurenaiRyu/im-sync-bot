package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.*
import kurenai.imsyncbot.sqlClient
import kurenai.imsyncbot.utils.withIO
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.bot
import net.mamoe.mirai.message.data.source
import org.babyfish.jimmer.sql.kt.ast.expression.eq

/**
 * @author Kurenai
 * @since 2023/6/3 16:36
 */

object QQMessageRepository: BaseRepository<QQMessage, Long>() {

    suspend fun findByChain(chain: MessageChain) =
        findByBotIdAndTargetIdAndMessageId(chain.bot.id, chain.source.targetId, chain.source.ids[0])

    suspend fun findByBotIdAndTargetIdAndMessageId(botId: Long, targetId: Long, messageId: Int): QQMessage? = withIO {
        sqlClient.createQuery(QQMessage::class) {
            where(
                table.botId eq botId,
                table.targetId eq targetId,
                table.messageId eq messageId,
            )
            select(table)
        }.fetchOneOrNull()
    }

}