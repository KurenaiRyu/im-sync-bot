package kurenai.imsyncbot

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.vertx.core.Vertx
import it.tdlight.Init
import kurenai.imsyncbot.bot.discord.DiscordBot
import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.domain.UserConfig
import kurenai.imsyncbot.jimmer.SqliteDialect
import kurenai.imsyncbot.jimmer.scalar.GroupStatusScalarProvider
import kurenai.imsyncbot.jimmer.scalar.UserStatusScalarProvider
import kurenai.imsyncbot.utils.*
import net.mamoe.mirai.Bot
import okio.Path
import okio.Path.Companion.toPath
import org.babyfish.jimmer.sql.event.TriggerType
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.ConnectionManager
import org.slf4j.Logger
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask
import kotlin.io.path.inputStream

/**
 * @author Kurenai
 * @since 7/1/2022 09:31:04
 */

internal val log: Logger = getLogger()
internal val snowFlake = SnowFlake(1)
internal val dfs: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

//internal val callbacks = reflections.getSubTypesOf(Callback::class.java).map { it.getConstructor().newInstance() }
lateinit var configProperties: ConfigProperties
lateinit var sqlClient: KSqlClient
lateinit var vertx: Vertx

internal lateinit var instants: MutableList<ImSyncBot>
internal lateinit var imSyncBot: ImSyncBot
internal lateinit var discordBot: DiscordBot

suspend fun main() {
    Init.init() //td-lib
    initProperties()
    initDB()
    initFileServer()
    imSyncBot = ImSyncBot(configProperties)
    imSyncBot.start()
    commonInit()
}

private fun initProperties() {
    fs.list(".".toPath()).filter {
        it.name.endsWith(".env") && it.name != "example.env" && !fs.metadata(it).isDirectory
    }.firstOrNull()?.let {
        val pop = Properties()
        it.toNioPath().inputStream().use { input ->
            pop.load(input)
            setEnv(pop)
        }
    }

    val configPath = "config.yaml".toPath()
    configProperties = yamlMapper.readValue(fs.read(configPath) { readByteArray() }, ConfigProperties::class.java)
}

private fun initFileServer() {
    vertx = Vertx.vertx()
    vertx.deployVerticle(WebApplication::class.qualifiedName)
}

private fun initDB() {
    val initSql =
        Bot::class.java.getResourceAsStream("/init.sql").buffered().use { stream -> stream.readAllBytes() }
            .decodeToString()

    val config = HikariConfig()
    config.jdbcUrl = "jdbc:sqlite:im-sync-bot.db"
    config.driverClassName = "org.sqlite.JDBC"
    config.isAutoCommit = true
    config.maximumPoolSize = 1
    config.connectionInitSql = initSql

    sqlClient = newKSqlClient {
        setTriggerType(TriggerType.BINLOG_ONLY)
        setDialect(SqliteDialect())
        setScalarProvider(GroupConfig::status, GroupStatusScalarProvider())
        setScalarProvider(UserConfig::status, UserStatusScalarProvider())
        setConnectionManager(ConnectionManager.simpleConnectionManager(HikariDataSource(config)))
    }
}

private fun commonInit() {
//    registerQQCommand()
    //TODO: 设置 inline 命令
//    registerInlineCommand()
//    registerQQHandler()
    setUpTimer()
}

private val cacheAllowSize = 100 * 1024 * 1024L

private const val cachePath = "./cache"
private val clearCacheTimer = Timer("ClearCache", true)

private fun setUpTimer() {
    clearCacheTimer.scheduleAtFixedRate(timerTask {
        runCatching {
            val cacheDir = cachePath.toPath(true)
            if (!fs.exists(cacheDir)) fs.createDirectories(cacheDir)

            for (subDir in fs.list(cacheDir).filter { fs.metadata(it).isDirectory }) {
                val list = fs.list(subDir)
                    .map { fs.metadata(it) to it }
                    .sortedBy { (metadata, _) -> metadata.lastModifiedAtMillis }

                var totalSize = list.sumOf { (metadata, _) -> metadata.size ?: 0 }
                if (totalSize < cacheAllowSize) continue

                val fileToDelete = mutableListOf<Path>()
                for ((metadata, path) in list) {
                    fileToDelete.add(path)
                    totalSize -= metadata.size ?: 0
                    if (totalSize < cacheAllowSize) break
            }

                doDeleteCacheFile(fileToDelete)
                log.info("Cache folder [${subDir.name}] size: ${totalSize.humanReadableByteCountBin()}")
        }
        }.onFailure {
            log.error("Clear cache file error", it)
        }
    }, 5000L, TimeUnit.HOURS.toMillis(1))
}

private fun doDeleteCacheFile(filesToDelete: List<Path>) {
    if (filesToDelete.isNotEmpty()) {
        log.info("Clearing cache files...")
        filesToDelete.forEach { fs.delete(it) }
        log.debug("Deleted files: {}", filesToDelete.joinToString(", ") { it.name })
        log.info("Clear ${filesToDelete.size} cache files.")
    }
}