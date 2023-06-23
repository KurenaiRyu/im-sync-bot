package kurenai.imsyncbot.service

import kotlinx.coroutines.runBlocking
import kurenai.imsyncbot.qqMessageRepository
import kurenai.imsyncbot.qqTgRepository
import kurenai.imsyncbot.repository.QQMessageRepository
import kurenai.imsyncbot.repository.QQTgRepository
import kurenai.imsyncbot.utils.withIO
import net.mamoe.mirai.message.data.MessageChain
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.jvm.optionals.getOrNull

/**
 * @author Kurenai
 * @since 2023/6/12 1:40
 */

@SpringBootTest
class MessageServiceTest {

    @Autowired
    lateinit var qqTgRepository: QQTgRepository
    @Autowired
    lateinit var qqMessageRepository: QQMessageRepository

    @Test
    fun findQQByTg() {
        runBlocking {
            val result = withIO {
                qqTgRepository.findByTgGrpIdAndTgMsgId(-1001250114081L, 107971870720)?.let {
                    println(it)
                    qqMessageRepository.findById(it.qqId).getOrNull()
                }?.let {
                    println(it)
                    MessageChain.deserializeFromJsonString(it.json)
                }
            }
            println("=======================")
            println(result)
        }
    }
}