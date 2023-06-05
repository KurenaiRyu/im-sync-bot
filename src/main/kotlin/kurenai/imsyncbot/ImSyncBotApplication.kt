package kurenai.imsyncbot

import kurenai.imsyncbot.repository.QQMessageRepository
import kurenai.imsyncbot.repository.QQTgRepository
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactories
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.data.jpa.repository.config.EnableJpaRepositories


/**
 * @author Kurenai
 * @since 2023/6/3 16:29
 */

@SpringBootApplication
@EnableJpaRepositories
class ImSyncBotApplication

lateinit var qqMessageRepository: QQMessageRepository
lateinit var qqTgRepository: QQTgRepository
lateinit var applicationContext: ApplicationContext

suspend fun main(args: Array<String>) {
    applicationContext = runApplication<ImSyncBotApplication>(*args) {
        this.setBannerMode(Banner.Mode.OFF)
    }
    qqMessageRepository = applicationContext.getBean(QQMessageRepository::class.java)
    qqTgRepository = applicationContext.getBean(QQTgRepository::class.java)
    start()
}