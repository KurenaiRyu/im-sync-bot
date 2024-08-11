package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.QQMessage
import org.springframework.data.jpa.repository.JpaRepository

/**
 * @author Kurenai
 * @since 2023/6/3 16:36
 */

interface QQMessageRepository : JpaRepository<QQMessage, Long> {

    fun findByBotIdAndTargetIdAndMessageId(botId: Long, targetId: Long, messageId: Int): QQMessage?

}