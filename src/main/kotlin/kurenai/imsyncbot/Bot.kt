package kurenai.imsyncbot

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.command.AbstractInlineCommand
import kurenai.imsyncbot.command.AbstractQQCommand
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.AbstractConfig
import kurenai.imsyncbot.qq.QQHandler
import kurenai.imsyncbot.qq.QQMessageHandler
import kurenai.imsyncbot.telegram.TelegramHandler
import kurenai.imsyncbot.telegram.TgMessageHandler
import kurenai.imsyncbot.utils.SnowFlake
import kurenai.imsyncbot.utils.humanReadableByteCountBin
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AgeFileFilter
import org.apache.commons.io.filefilter.SizeFileFilter
import org.apache.commons.lang3.time.DateUtils
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.reflections.Reflections
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

/**
 * @author Kurenai
 * @since 7/1/2022 09:31:04
 */

internal val log: Logger = LogManager.getLogger()
internal val mapper: ObjectMapper = jacksonObjectMapper()
    .registerModules(Jdk8Module(), JavaTimeModule())
    .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
    .activateDefaultTyping(
        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Any::class.java).build(),
        ObjectMapper.DefaultTyping.EVERYTHING
    )
internal val snowFlake = SnowFlake(1)
internal val reflections = Reflections("kurenai.imsyncbot")
internal val dfs: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal val configs = ArrayList<AbstractConfig<*>>()
internal val callbacks = reflections.getSubTypesOf(Callback::class.java).map { it.getConstructor().newInstance() }
internal val tgCommands = ArrayList<AbstractTelegramCommand>()
internal val qqCommands = ArrayList<AbstractQQCommand>()
internal val inlineCommands = HashMap<String, AbstractInlineCommand>()
internal val qqHandlers = ArrayList<QQHandler>()
internal val tgHandlers = ArrayList<TelegramHandler>()

internal lateinit var instants: MutableList<ImSyncBot>

//suspend fun main() {
//    instants = loadInstants()
//    configs.first()
//    commonInit()
//    instants.forEach { it.start() }
//}

suspend fun start() {
    instants = loadInstants()
    configs.first()
    commonInit()
    instants.forEach { it.start() }
}

fun loadInstants() = File("./config")
    .walk()
    .mapNotNull { file ->
        if ((file.name == "config.yaml" || file.name == "config.yml") && !file.parentFile.name.startsWith('.')) {
            loadConfig(file)?.takeIf { it.enable }?.let { file.parentFile.path to it }
        } else null
    }
    .map { (path, props) -> ImSyncBot(path, props) }
    .toMutableList()
    .takeIf { it.isNotEmpty() } ?: throw IllegalStateException("找不到配置文件，请确认配置文件配置是否正确并且开启，或者路径是否在 ./config/config.yaml")

fun commonInit() {
    registerTgCommand()
    registerQQCommand()
    //TODO: 设置 inline 命令
//    registerInlineCommand()
    registerTgHandler()
    registerQQHandler()
    setUpTimer()
}

fun registerTgHandler() {
    reflections.getSubTypesOf(TelegramHandler::class.java)
        .filter { it != TgMessageHandler::class.java }
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach {
            tgHandlers.add(it)
            log.info("Registered telegram handler:  ${it.handleName()}(${it::class.java.simpleName})")
        }
}

fun registerQQHandler() {
    reflections.getSubTypesOf(QQHandler::class.java)
        .filter { it != QQMessageHandler::class.java }
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach {
            qqHandlers.add(it)
            log.info("Registered qq handler:  ${it.handleName()}(${it::class.java.simpleName})")
        }
}

private fun registerTgCommand() {
    reflections.getSubTypesOf(AbstractTelegramCommand::class.java)
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach { command ->
            tgCommands.removeIf { it.name == command.name }
            tgCommands.add(command)
            log.info("Registered telegram command:  ${command.name}(${command::class.java.simpleName})")
        }
}

private fun registerQQCommand() {
    reflections.getSubTypesOf(AbstractQQCommand::class.java)
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach { command ->
            qqCommands.add(command)
            log.info("Registered qq command:  ${command.name}(${command::class.java.simpleName})")
        }
}

private fun registerInlineCommand() {
    reflections.getSubTypesOf(AbstractInlineCommand::class.java)
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach { command ->
            if (command.command.isNotBlank()) {
                inlineCommands[command.command] = command
            }
            log.info("Registered inline command:  ${command.name}(${command::class.java.simpleName})")
        }
}

private val largeFileSize = 200 * 1024L
private val largeDirSize = 100 * 1024 * 1024L

private const val cachePath = "./cache"
private val clearCacheTimer = Timer("ClearCache", true)

@Suppress("UNCHECKED_CAST")
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

                val sizeOfDir = FileUtils.sizeOfDirectory(dirFile)
                log.info("Cache folder [${dirFile.name}] size: ${sizeOfDir.humanReadableByteCountBin()}")
                val filesToDelete = ArrayList<File>()

                val oldestAllowedFileDate = DateUtils.addMinutes(Date(), -60)
                if (sizeOfDir > largeDirSize) {
                    filesToDelete.addAll(
                        FileUtils.listFiles(
                            dirFile,
                            SizeFileFilter(largeFileSize),
                            null
                        ) as Collection<File>
                    )
                    filesToDelete.addAll(
                        FileUtils.listFiles(
                            dirFile,
                            AgeFileFilter(oldestAllowedFileDate),
                            null
                        ) as Collection<File>
                    )
                }
                doDeleteCacheFile(filesToDelete)
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }, 5000L, TimeUnit.MINUTES.toMillis(60))
}

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