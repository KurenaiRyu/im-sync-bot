package kurenai.mybot

import kurenai.mybot.qq.QQBotClient
import kurenai.mybot.telegram.TelegramBotClient

open class BotContext(open val telegramBotClient: TelegramBotClient, open val qqBotClient: QQBotClient)