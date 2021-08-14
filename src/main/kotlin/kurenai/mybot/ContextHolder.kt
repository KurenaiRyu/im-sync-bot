package kurenai.mybot

import kurenai.mybot.qq.QQBotClient
import kurenai.mybot.telegram.TelegramBotClient

object ContextHolder {
    lateinit var qqBotClient: QQBotClient
    lateinit var telegramBotClient: TelegramBotClient
    val qqTgBinding = HashMap<Long, Long>()
    val tgQQBinding = HashMap<Long, Long>()
    var defaultQQGroup: Long = 0L
    var defaultTgGroup: Long = 0L
    var masterOfQQ: Long = 0L
    var masterOfTg: Long = 0L
    var masterChatId: Long = 0L
}