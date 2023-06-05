package kurenai.imsyncbot

import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.repository.QQMessageRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

/**
 * @author Kurenai
 * @since 2023/6/4 20:58
 */

@SpringBootTest(classes = [ImSyncBotApplication::class])
class RepositoryTest {

    @Autowired
    lateinit var qqMessageRepository: QQMessageRepository

    @Test
    fun test() {
        val msg = qqMessageRepository.save(
            QQMessage(
                1, 2, 3, 4, 5, QQMessage.QQMessageType.GROUP, "", false, LocalDateTime.now()
            )
        )
        msg.botId = 333
        val msg2 = qqMessageRepository.save(msg)
        println(msg.botId)
        println(msg2.botId)
        qqMessageRepository.delete(msg)
    }

}