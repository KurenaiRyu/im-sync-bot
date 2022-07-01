package kurenai.imsyncbot

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.kurenairyu.cache.Cache
import io.github.kurenairyu.cache.redis.lettuce.LettuceCache
import io.github.kurenairyu.cache.redis.lettuce.jackson.JacksonCodec
import io.github.kurenairyu.cache.redis.lettuce.jackson.RecordNamingStrategyPatchModule
import io.lettuce.core.RedisURI
import kurenai.imsyncbot.Main.Companion.log
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.command.AbstractQQCommand
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.command.InlineCommandHandler
import kurenai.imsyncbot.config.AbstractConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.handler.qq.QQHandler
import kurenai.imsyncbot.handler.qq.QQMessageHandler
import kurenai.imsyncbot.handler.tg.TelegramHandler
import kurenai.imsyncbot.handler.tg.TgMessageHandler
import kurenai.imsyncbot.qq.QQBotClient
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBot
import kurenai.imsyncbot.utils.HttpUtil
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AgeFileFilter
import org.apache.commons.io.filefilter.SizeFileFilter
import org.apache.commons.lang3.time.DateUtils
import org.apache.logging.log4j.LogManager
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.codec.JsonJacksonCodec
import org.redisson.config.Config
import org.reflections.Reflections
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

/**
 * @author Kurenai
 * @since 7/1/2022 09:31:04
 */

class Main {
    companion object {
        val log = LogManager.getLogger()
    }
}

val mapper = jacksonObjectMapper()
    .registerModules(Jdk8Module(), JavaTimeModule(), RecordNamingStrategyPatchModule())
    .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
    .activateDefaultTyping(
        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Any::class.java).build(),
        ObjectMapper.DefaultTyping.EVERYTHING
    )

val reflections = Reflections("kurenai.imsyncbot")
val dfs = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

val configs = ArrayList<AbstractConfig<*>>()
val callbacks = reflections.getSubTypesOf(Callback::class.java).map { it.getConstructor().newInstance() }
val tgCommands = ArrayList<AbstractTelegramCommand>()
val qqCommands = ArrayList<AbstractQQCommand>()
val inlineCommands = HashMap<String, InlineCommandHandler>()
val qqHandlers = ArrayList<QQHandler>()
val tgHandlers = ArrayList<TelegramHandler>()

lateinit var tgMessageHandler: TgMessageHandler
lateinit var qqMessageHandler: QQMessageHandler
lateinit var configProperties: ConfigProperties
lateinit var redisson: RedissonClient
lateinit var cache: Cache

fun main() {
    start()
}

fun start() {
    loadProperties()
    init()
    QQBotClient.start()
    TelegramBot.start()
}

fun loadProperties() {
    val mapper = ObjectMapper(YAMLFactory()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
    configProperties = mapper.readValue(File("./config/config.yaml"), ConfigProperties::class.java)
}

fun init() {
    redisson = redissonClient()
    cache = cache()

    checkRedisCodec()
    configProxy()
    setUpTimer()
    initUserConfig()

    registerTgCommand()
    registerQQCommand()
    registerTgHandler()
    registerQQHandler()

}

fun registerTgHandler() {
    reflections.getSubTypesOf(TelegramHandler::class.java)
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach {
            tgHandlers.add(it)
            if (!::tgMessageHandler.isInitialized && it is TgMessageHandler) tgMessageHandler = it
            log.info("Registered telegram handler:  ${it.handleName()}(${it::class.java.simpleName})")
        }
}

fun registerQQHandler() {
    reflections.getSubTypesOf(QQHandler::class.java)
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach {
            qqHandlers.add(it)
            if (!::qqMessageHandler.isInitialized && it is QQMessageHandler) qqMessageHandler = it
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

private fun redissonClient(): RedissonClient {
    val redissonConfig = Config()
    redissonConfig.codec = JsonJacksonCodec(mapper)
    redissonConfig.useSingleServer()
        .setAddress("redis://${configProperties.redis.host}:${configProperties.redis.port}")
        .setDatabase(configProperties.redis.database)
    return Redisson.create(redissonConfig)
}

private fun cache(): Cache {
    val redisURI = RedisURI.builder()
        .withHost(configProperties.redis.host)
        .withPort(configProperties.redis.port)
        .withDatabase(configProperties.redis.database)
        .build()
    return LettuceCache(
        redisURI, JacksonCodec<Any>(mapper)
    )
}

private fun initUserConfig() {
    UserConfig.setMaster(configProperties.handler)
}

private fun configProxy() {
    val proxy = configProperties.bot.telegram.proxy
    if (proxy.type != Proxy.Type.DIRECT) {
        HttpUtil.PROXY = Proxy(proxy.type, InetSocketAddress(proxy.host, proxy.port))
    }
}

private val largeFileSize = 200 * 1024L
private val largeDirSize = 100 * 1024 * 1024L

private val cacheDirs = listOf("./cache/img", "./cache/doc", "./cache/file")
private val clearCacheTimer = Timer("ClearCache", true)

private fun setUpTimer() {
    clearCacheTimer.scheduleAtFixedRate(timerTask {
        for (cacheDir in cacheDirs) {
            try {
                val dir = File(cacheDir)
                val sizeOfDir = FileUtils.sizeOfDirectory(dir)
                log.info("Cache folder [${dir.name}] size: ${sizeOfDir.humanReadableByteCountBin()}")
                val filesToDelete = ArrayList<File>()
                val files = CacheService.getNotExistFiles()?.map { File(it.value) }
                if (files?.isNotEmpty() == true) filesToDelete.addAll(filesToDelete)

                val oldestAllowedFileDate = DateUtils.addMinutes(Date(), -10)
                if (sizeOfDir > largeDirSize) {
                    filesToDelete.addAll(
                        FileUtils.listFiles(
                            dir,
                            SizeFileFilter(largeFileSize),
                            null
                        ) as Collection<File>
                    )
                    filesToDelete.addAll(
                        FileUtils.listFiles(
                            dir,
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
    }, 5000L, TimeUnit.MINUTES.toMillis(10))
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

private fun checkRedisCodec() {
    val tgProperties = configProperties.bot.telegram
    val serializeType: String? = cache.get("SERIALIZE_TYPE", tgProperties.token.substringBefore(":"))
    if ("json" != serializeType?.lowercase()) {
        cache.clearAll()
        cache.put("SERIALIZE_TYPE", tgProperties.token.substringBefore(":"), "json")
    }
}