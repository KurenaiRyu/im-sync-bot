package kurenai.imsyncbot

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.domain.UserConfig
import kurenai.imsyncbot.jimmer.SqliteDialect
import kurenai.imsyncbot.jimmer.scalar.GroupStatusScalarProvider
import kurenai.imsyncbot.jimmer.scalar.UserStatusScalarProvider
import kurenai.imsyncbot.utils.setEnv
import org.babyfish.jimmer.sql.event.TriggerType
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.ConnectionManager
import org.yaml.snakeyaml.Yaml
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

class ImSyncBotApplication

lateinit var configProperties: ConfigProperties
lateinit var sqlClient: KSqlClient

suspend fun main(args: Array<String>) {
    initProperties()
    initDB()
    start()
}

fun initProperties() {
    Files.list(Path.of(".")).filter {
        it.name.endsWith(".env") && it.name != "example.env" && !it.isDirectory()
    }.findFirst().ifPresent {
        val pop = Properties()
        it.inputStream().use { stream ->
            pop.load(stream)
            setEnv(pop)
        }
    }

    val configPath = Path.of("config.yaml")
    configProperties = Yaml().loadAs(Files.readString(configPath), ConfigProperties::class.java)
}

fun initDB() {
    val config = HikariConfig()
    config.jdbcUrl = "jdbc:sqlite:im-sync-bot.db"
    config.driverClassName = "org.sqlite.JDBC"
    config.isAutoCommit = true
    config.maximumPoolSize = 1
    sqlClient = newKSqlClient {
        setTriggerType(TriggerType.BINLOG_ONLY)
        setDialect(SqliteDialect())
        setScalarProvider(GroupConfig::status, GroupStatusScalarProvider())
        setScalarProvider(UserConfig::status, UserStatusScalarProvider())
        setConnectionManager(ConnectionManager.simpleConnectionManager(HikariDataSource(config)))
    }
}