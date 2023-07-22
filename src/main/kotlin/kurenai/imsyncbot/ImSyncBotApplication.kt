package kurenai.imsyncbot

import jakarta.persistence.EntityManager
import kurenai.imsyncbot.repository.*
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
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
lateinit var fileCacheRepository: FileCacheRepository
lateinit var groupConfigRepository: GroupConfigRepository
lateinit var qqDiscordRepository: QqDiscordRepository
lateinit var applicationContext: ApplicationContext

suspend fun main(args: Array<String>) {
    applicationContext = runApplication<ImSyncBotApplication>(*args) {
        this.setBannerMode(Banner.Mode.OFF)
    }
    qqMessageRepository = applicationContext.getBean(QQMessageRepository::class.java)
    qqTgRepository = applicationContext.getBean(QQTgRepository::class.java)
    fileCacheRepository = applicationContext.getBean(FileCacheRepository::class.java)
    groupConfigRepository = applicationContext.getBean(GroupConfigRepository::class.java)
    qqDiscordRepository = applicationContext.getBean(QqDiscordRepository::class.java)
    start()
}

@Configuration
class JpaConfig {

    fun config(manager: EntityManager) {
    }

}