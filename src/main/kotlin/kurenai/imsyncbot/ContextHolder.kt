package kurenai.imsyncbot

import kurenai.imsyncbot.telegram.TelegramBotClient
import net.mamoe.mirai.Bot

object ContextHolder {
    lateinit var qqBot: Bot
    lateinit var telegramBotClient: TelegramBotClient
    val qqTgBinding = HashMap<Long, Long>()
    val tgQQBinding = HashMap<Long, Long>()
    var defaultQQGroup: Long = 0L
    var defaultTgGroup: Long = 0L
    var masterOfQQ: Long = 0L
    var masterOfTg: Long = 0L
    var masterChatId: Long = 0L
}