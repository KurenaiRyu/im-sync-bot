package kurenai.imsyncbot

import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBot
import moe.kurenai.tdlight.client.TDLightClient
import net.mamoe.mirai.Bot
import java.time.format.DateTimeFormatter

object ContextHolder {
    lateinit var qqBot: Bot
    lateinit var telegramBot: TelegramBot
    lateinit var cacheService: CacheService
    lateinit var tdClient: TDLightClient
    val dfs = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
}