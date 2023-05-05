package kurenai.imsyncbot

import io.github.kurenairyu.cache.Cache
import io.github.kurenairyu.cache.redis.lettuce.LettuceCache
import io.github.kurenairyu.cache.redis.lettuce.jackson.JacksonCodec
import io.lettuce.core.RedisURI
import kotlinx.coroutines.*
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.qq.QQMessageHandler
import kurenai.imsyncbot.telegram.TgMessageHandler
import kurenai.imsyncbot.qq.QQBot
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBot
import kurenai.imsyncbot.utils.childScopeContext
import moe.kurenai.tdlight.model.message.Message
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.NormalMember
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.message.data.source
import net.mamoe.mirai.utils.LoggerAdapters
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
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
        .plus(this).childScopeContext()
        .apply {
            job.invokeOnCompletion {
                kotlin.runCatching {
                    instants.remove(this)
                }.onFailure {
                    if (it !is CancellationException) TelegramBot.log.error(it.message, it)
                }
            }
        }
    internal val redisson: RedissonClient = configRedisson()
    internal val cache: Cache = configCache()
    internal val proxy: Proxy? = configProxy()
    internal val userConfig: UserConfig = UserConfig(configPath, configProperties)
    internal val groupConfig: GroupConfig = GroupConfig(configPath)
    val tgMessageHandler: TgMessageHandler = TgMessageHandler(configProperties, this)
    var qqMessageHandler: QQMessageHandler = QQMessageHandler(configProperties, this)
    internal val qq: QQBot = QQBot(coroutineContext, configProperties.bot.qq, this)
    internal val tg: TelegramBot = TelegramBot(coroutineContext, configProperties.bot.telegram, this)
    internal val privateHandle = PrivateChatHandler(configProperties)

    init {
        //mirai使用log4j2
        LoggerAdapters.useLog4j2()

        if (configProperties.debug) {
            Configurator.setLevel("kurenai.imsyncbot", Level.DEBUG)
        }
        checkRedisCodec()
        configProxy()
    }

    suspend fun start() {
        withContext(this@ImSyncBot.coroutineContext) {
            log.info("Start im-sync-bot $name ...")
            log.info("Telegram bot ${configProperties.bot.telegram.username}")
            log.info("QQ bot ${configProperties.bot.qq.account}")
            tg.start()
            qq.start()
        }
    }

    suspend fun getMemberFromMessage(message: Message): NormalMember {
        val tgId = message.from?.id ?: throw BotException("未找到该消息发送用户id")
        val group = getGroupFromMessage(message)
        val qq = if (tgId == tg.tgBot.me.id) { //bot id
            CacheService.getQQByTg(message)?.source?.fromId ?: throw BotException("未找到该用户qq")
        } else {
            userConfig.links.firstOrNull { it.tg == tgId }?.qq ?: throw BotException("该用户未绑定qq")
        }
        return group.getMember(qq) ?: throw BotException("未找到该成员")
    }

    suspend fun getMemberFromMessageReply(message: Message) = getMemberFromMessage(message.replyToMessage ?: throw BotException("未找到该消息的引用"))

    fun getGroupFromMessage(message: Message): Group {
        val groupId = groupConfig.tgQQ[message.chat.id] ?: throw BotException("未找到该qq群id")
        return qq.qqBot.getGroup(groupId) ?: throw BotException("未找到该qq群")
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
        val serializeType: String? = redisson.getBucket<String>("SERIALIZE_TYPE:${tgProperties.token.substringBefore(":")}").get()
        if ("json" != serializeType?.lowercase()) {
            cache.clearAll()
            cache.put("SERIALIZE_TYPE", tgProperties.token.substringBefore(":"), "json")
        }
    }

    private fun configProxy(): Proxy? {
        val configProxy = configProperties.bot.telegram.proxy ?: return null
        return if (configProxy.type != Proxy.Type.DIRECT) {
            Proxy(configProxy.type, InetSocketAddress(configProxy.host, configProxy.port))
        } else {
            null
        }
    }
}

suspend fun getBot(): ImSyncBot? = runCatching { getBotOrThrow() }.getOrNull()
suspend fun getBotOrThrow(): ImSyncBot = coroutineContext[ImSyncBot] ?: throw IllegalArgumentException("找不到当前Bot实例")