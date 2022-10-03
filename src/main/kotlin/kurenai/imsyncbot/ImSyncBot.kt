package kurenai.imsyncbot

import io.github.kurenairyu.cache.Cache
import io.github.kurenairyu.cache.redis.lettuce.LettuceCache
import io.github.kurenairyu.cache.redis.lettuce.jackson.JacksonCodec
import io.lettuce.core.RedisURI
import kotlinx.coroutines.*
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.handler.qq.QQMessageHandler
import kurenai.imsyncbot.handler.tg.TgMessageHandler
import kurenai.imsyncbot.qq.QQBotClient
import kurenai.imsyncbot.telegram.TelegramBot
import kurenai.imsyncbot.utils.childScopeContext
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.codec.JsonJacksonCodec
import org.redisson.config.Config
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * @author Kurenai
 * @since 2022/10/3 8:48
 */

class ImSyncBot(
    internal val configPath: String,
    internal val configProperties: ConfigProperties
) : CoroutineScope, AbstractCoroutineContextElement(ImSyncBot) {

    companion object Key : CoroutineContext.Key<ImSyncBot>

    internal val name: String = File(configPath).name.let { if (it == "config") "root" else it }
    override val coroutineContext: CoroutineContext = CoroutineName(name)
        .plus(this)
        .plus(
            CoroutineExceptionHandler { context, e ->
                TelegramBot.log.error(context[CoroutineName]?.let { "Exception in coroutine '${it.name}'." }
                    ?: "Exception in unnamed coroutine.", e)
            }).childScopeContext()
        .apply {
            job.invokeOnCompletion {
                kotlin.runCatching {
                    instants.remove(this)
                }.onFailure {
                    if (it !is CancellationException) TelegramBot.log.error(it)
                }
            }
        }
    internal val redisson: RedissonClient = configRedisson()
    internal val cache: Cache = configCache()
    internal val proxy: Proxy? = configProxy()
    internal val userConfig: UserConfig = UserConfig(configPath).apply {
        setMaster(configProperties.handler)
    }
    internal val groupConfig: GroupConfig = GroupConfig(configPath)
    val tgMessageHandler: TgMessageHandler = TgMessageHandler(configProperties, this)
    var qqMessageHandler: QQMessageHandler = QQMessageHandler(configProperties, this)
    internal val qq: QQBotClient = QQBotClient(coroutineContext, configProperties.bot.qq, this)
    internal val tg: TelegramBot = TelegramBot(coroutineContext, configProperties.bot.telegram, this)
    internal val privateHandle = PrivateChatHandler(configProperties)

    init {
        checkRedisCodec()
        configProxy()
    }

    suspend fun start() {
        withContext(this@ImSyncBot.coroutineContext) {
            log.info("Start im-sync-bot $name ...")
            log.info("Telegram bot ${configProperties.bot.telegram.username}")
            log.info("QQ bot ${configProperties.bot.qq.account}")
            qq.start()
            tg.start()
        }
    }

    private fun configRedisson(): RedissonClient {
        val redissonConfig = Config()
        redissonConfig.codec = JsonJacksonCodec(mapper)
        val server = redissonConfig.useSingleServer()
        configProperties.redis.url?.let {
            server.address = it
        } ?: kotlin.run {
            server.address = "redis://${configProperties.redis.host}:${configProperties.redis.port}"
            server.database = configProperties.redis.database
        }
        return Redisson.create(redissonConfig)
    }

    private fun configCache(): Cache {
        val redisURI = configProperties.redis.url?.let {
            RedisURI.create(it)
        } ?: kotlin.run {
            RedisURI.builder()
                .withHost(configProperties.redis.host)
                .withPort(configProperties.redis.port)
                .withDatabase(configProperties.redis.database)
                .build()
        }
        return LettuceCache(
            redisURI, JacksonCodec<Any>(mapper)
        )
    }

    private fun checkRedisCodec() {
        val tgProperties = configProperties.bot.telegram
        val serializeType: String? = cache.get("SERIALIZE_TYPE", tgProperties.token.substringBefore(":"))
        if ("json" != serializeType?.lowercase()) {
            cache.clearAll()
            cache.put("SERIALIZE_TYPE", tgProperties.token.substringBefore(":"), "json")
        }
    }

    private fun configProxy(): Proxy? {
        val configProxy = configProperties.bot.telegram.proxy
        return if (configProxy.type != Proxy.Type.DIRECT) {
            Proxy(configProxy.type, InetSocketAddress(configProxy.host, configProxy.port))
        } else {
            null
        }
    }
}

suspend fun getBot(): ImSyncBot? = runCatching { getBotOrThrow() }.getOrNull()
suspend fun getBotOrThrow(): ImSyncBot = coroutineContext[ImSyncBot] ?: throw IllegalArgumentException("找不到当前Bot实例")