package kurenai.mybot

import kurenai.mybot.qq.QQBotClient
import kurenai.mybot.telegram.TelegramBotClient

object ContextHolder {
    var qqBotClient: QQBotClient? = null
    var telegramBotClient: TelegramBotClient? = null
}