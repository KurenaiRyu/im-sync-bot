package kurenai.imsyncbot

import kurenai.imsyncbot.qq.QQBotClient
import kurenai.imsyncbot.telegram.TelegramBotClient

open class BotContext(
    open val telegramBotClient: TelegramBotClient,
    open val qqBotClient: QQBotClient,
)