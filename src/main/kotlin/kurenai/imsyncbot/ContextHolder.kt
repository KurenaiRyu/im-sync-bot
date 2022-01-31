package kurenai.imsyncbot

import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBotClient
import net.mamoe.mirai.Bot
import java.time.format.DateTimeFormatter

object ContextHolder {
    lateinit var qqBot: Bot
    lateinit var telegramBotClient: TelegramBotClient
    lateinit var cacheService: CacheService
    val dfs = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
}