package kurenai.imsyncbot

import com.linecorp.kotlinjdsl.spring.data.SpringDataQueryFactory
import jakarta.persistence.EntityManager
import kurenai.imsyncbot.repository.*
import kurenai.imsyncbot.utils.setEnv
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.Property
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name


/**
 * @author Kurenai
 * @since 2023/6/3 16:29
 */

@SpringBootApplication
@EnableJpaRepositories
@EnableConfigurationProperties(ConfigProperties::class)
class ImSyncBotApplication

lateinit var qqMessageRepository: QQMessageRepository
lateinit var qqTgRepository: QQTgRepository
lateinit var fileCacheRepository: FileCacheRepository
lateinit var groupConfigRepository: GroupConfigRepository
lateinit var userConfigRepository: UserConfigRepository
lateinit var qqDiscordRepository: QqDiscordRepository
lateinit var applicationContext: ApplicationContext
lateinit var queryFactory: SpringDataQueryFactory
lateinit var configProperties: ConfigProperties

suspend fun main(args: Array<String>) {
    initProperties()
    applicationContext = runApplication<ImSyncBotApplication>(*args) {
        this.setBannerMode(Banner.Mode.OFF)
    }
    qqMessageRepository = applicationContext.getBean(QQMessageRepository::class.java)
    qqTgRepository = applicationContext.getBean(QQTgRepository::class.java)
    fileCacheRepository = applicationContext.getBean(FileCacheRepository::class.java)
    groupConfigRepository = applicationContext.getBean(GroupConfigRepository::class.java)
    userConfigRepository = applicationContext.getBean(UserConfigRepository::class.java)
    qqDiscordRepository = applicationContext.getBean(QqDiscordRepository::class.java)
    queryFactory = applicationContext.getBean(SpringDataQueryFactory::class.java)
    configProperties = applicationContext.getBean(ConfigProperties::class.java)
    start()
}

fun initProperties() {
    Files.list(Path.of(".")).filter {
        it.name.endsWith(".env") && it.name != "example.env" && !it.isDirectory()
    }.findFirst().ifPresent {
        var pop = Properties()
        it.inputStream().use { stream ->
            pop.load(stream)
            setEnv(pop)
        }
    }
}

@Configuration
class JpaConfig {

    fun config(manager: EntityManager) {
    }

}