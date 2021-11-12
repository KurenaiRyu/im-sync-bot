package kurenai.imsyncbot

import kurenai.imsyncbot.telegram.TelegramBotClient
import net.mamoe.mirai.Bot
import java.util.concurrent.ConcurrentHashMap

object ContextHolder {
    lateinit var qqBot: Bot
    lateinit var telegramBotClient: TelegramBotClient
    val qqTgBinding = ConcurrentHashMap<Long, Long>()
    val tgQQBinding = ConcurrentHashMap<Long, Long>()
    var defaultQQGroup: Long = 0L
    var defaultTgGroup: Long = 0L
    var masterOfQQ = emptyList<Long>()
    var masterOfTg = emptyList<Long>()
    var masterChatId: Long = 0L
}