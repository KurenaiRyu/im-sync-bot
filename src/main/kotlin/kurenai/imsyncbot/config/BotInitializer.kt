package kurenai.imsyncbot.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.kurenairyu.cache.Cache
import io.github.kurenairyu.cache.redis.lettuce.LettuceCache
import io.github.kurenairyu.cache.redis.lettuce.jackson.JacksonCodec
import io.github.kurenairyu.cache.redis.lettuce.jackson.RecordNamingStrategyPatchModule
import io.lettuce.core.RedisURI
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.ContextHolder.cache
import kurenai.imsyncbot.ContextHolder.cacheService
import kurenai.imsyncbot.ContextHolder.config
import kurenai.imsyncbot.command.CommandHolder
import kurenai.imsyncbot.humanReadableByteCountBin
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.HttpUtil
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AgeFileFilter
import org.apache.commons.io.filefilter.SizeFileFilter
import org.apache.commons.lang3.time.DateUtils
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.codec.JsonJacksonCodec
import org.redisson.config.Config
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

object BotInitializer {

    private val log = KotlinLogging.logger {}
    private val largeFileSize = 200 * 1024L
    private val largeDirSize = 100 * 1024 * 1024L
    private val tgProperties = config.bot.telegram
    private val proxy = tgProperties.proxy

    private val cacheDirs = listOf("./cache/img", "./cache/doc", "./cache/file")
    private val clearCacheTimer = Timer("ClearCache", true)

    fun doInit() {
        initBot()
        initConfig()
    }

    fun initBot() {
        ContextHolder.mapper = objectMapper()
        cache = cache()
        ContextHolder.redisson = redissonClient()
        cacheService = CacheService()
    }

    private fun cache(): Cache {
        val redisURI = RedisURI.builder()
            .withHost(config.redis.host)
            .withPort(config.redis.port)
            .withDatabase(config.redis.database)
            .build()
        return LettuceCache(
            redisURI, JacksonCodec<Any>(ContextHolder.mapper)
        )
    }

    private fun redissonClient(): RedissonClient {
        val redissonConfig = Config()
        redissonConfig.codec = JsonJacksonCodec(ContextHolder.mapper)
        redissonConfig.useSingleServer()
            .setAddress("redis://${config.redis.host}:${config.redis.port}")
            .setDatabase(config.redis.database)
        return Redisson.create(redissonConfig)
    }

    private fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper()
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
    }

    fun initConfig() {
        checkRedisCodec()
        configProxy()
//        configImgBaseUrl()
        setUpTimer()
        initUserConfig()
        CommandHolder.init()
    }

    private fun initUserConfig() {
        UserConfig.setMaster(config.handler)
    }

    private fun configProxy() {
        if (proxy.type != Proxy.Type.DIRECT) {
            HttpUtil.PROXY = Proxy(proxy.type, InetSocketAddress(proxy.host, proxy.port))
        }
    }

//    private fun configImgBaseUrl() {
//        tgProperties.imgBaseUrl?.isNotBlank()?.let {
//            HttpUtil.IMG_BASE_URL = tgProperties.imgBaseUrl
//        }
//
//    }

    private fun setUpTimer() {
        clearCacheTimer.scheduleAtFixedRate(timerTask {
            for (cacheDir in cacheDirs) {
                try {
                    val dir = File(cacheDir)
                    val sizeOfDir = FileUtils.sizeOfDirectory(dir)
                    log.info { "Cache folder [${dir.name}] size: ${sizeOfDir.humanReadableByteCountBin()}" }
                    val filesToDelete = ArrayList<File>()
                    val files = cacheService.getNotExistFiles()?.map { File(it.value) }
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
            log.info { "Clearing cache files..." }
            filesToDelete.forEach {
                log.debug { "${it.name} deleted." }
                it.delete()
            } //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            log.info { "Clear ${filesToDelete.size} cache files." }
        }
    }

    private fun checkRedisCodec() {
        val serializeType: String? = cache.get("SERIALIZE_TYPE", tgProperties.token.substringBefore(":"))
        if ("json" != serializeType?.lowercase()) {
            cache.clearAll()
            cache.put("SERIALIZE_TYPE", tgProperties.token.substringBefore(":"), "json")
        }
    }
}