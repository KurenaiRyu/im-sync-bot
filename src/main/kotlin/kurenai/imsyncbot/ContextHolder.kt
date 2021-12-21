package kurenai.imsyncbot

import kurenai.imsyncbot.telegram.TelegramBotClient
import net.mamoe.mirai.Bot

object ContextHolder {
    lateinit var qqBot: Bot
    lateinit var telegramBotClient: TelegramBotClient
    var defaultQQGroup: Long = 0L
    var defaultTgGroup: Long = 0L
    var masterOfQQ = emptyList<Long>()
    var masterOfTg = emptyList<Long>()
    var masterChatId: Long = 0L
    var masterUsername: String = ""
}