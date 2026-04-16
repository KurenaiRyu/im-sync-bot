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
import org.babyfish.jimmer.sql.event.TriggerType
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.ConnectionManager
import org.slf4j.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name

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
    configProperties = yamlMapper.readValue(Files.readString(configPath), ConfigProperties::class.java)
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

private val largeFileSize = 200 * 1024L
private val cacheAllowSize = 100 * 1024 * 1024L

private const val cachePath = "./cache"
private val clearCacheTimer = Timer("ClearCache", true)

private fun setUpTimer() {
    clearCacheTimer.scheduleAtFixedRate(timerTask {
        val cacheDir = File(cachePath)
        cacheDir.mkdirs()
        for (dirFile in cacheDir.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
            try {
                if (!dirFile.exists()) {
                    log.warn("${dirFile.absolutePath} not exist!")
                    continue
                }

                val sizeOfDir = computeDirSize(dirFile)
                val filesToDelete = ArrayList<File>()
                if (sizeOfDir > cacheAllowSize) {
                    var deleteSize = 0L
                    val fileSet =
                        dirFile.listFiles()?.sortedByDescending { it.lastModified() }?.toMutableSet() ?: continue

                    // remove large file
                    fileSet.filter { f ->
                        f.toPath().fileSize() > largeFileSize
                    }.forEach {
                        deleteSize += Files.size(it.toPath())
                        fileSet.remove(it)
                        filesToDelete.add(it)
                    }

                    // remove until dir size less than allow cache size
                    for (file in fileSet) {
                        if (sizeOfDir - deleteSize > cacheAllowSize) {
                            deleteSize += Files.size(file.toPath())
                            filesToDelete.add(file)
                        } else
                            break
                    }
                    doDeleteCacheFile(filesToDelete)
                }
                log.info("Cache folder [${dirFile.name}] size: ${sizeOfDir.humanReadableByteCountBin()}")
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }, 5000L, TimeUnit.HOURS.toMillis(1))
}

private fun computeDirSize(dirFile: File) = dirFile.listFiles()?.sumOf { Files.size(it.toPath()) } ?: 0L

private fun doDeleteCacheFile(filesToDelete: List<File>) {
    if (filesToDelete.isNotEmpty()) {
        //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
        log.info("Clearing cache files...")
        filesToDelete.forEach {
            log.debug("${it.name} deleted.")
            it.delete()
        } //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
        log.info("Clear ${filesToDelete.size} cache files.")
    }
}