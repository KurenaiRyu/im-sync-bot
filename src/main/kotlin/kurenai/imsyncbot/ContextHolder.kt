package kurenai.imsyncbot

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kurenairyu.cache.Cache
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBot
import moe.kurenai.tdlight.client.TDLightClient
import net.mamoe.mirai.Bot
import org.redisson.api.RedissonClient
import java.time.format.DateTimeFormatter

object ContextHolder {
    val dfs = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    lateinit var qqBot: Bot
    lateinit var tdClient: TDLightClient
    lateinit var telegramBot: TelegramBot
    lateinit var cache: Cache
    lateinit var redisson: RedissonClient
    lateinit var cacheService: CacheService
    lateinit var config: ConfigProperties
    lateinit var mapper: ObjectMapper
}