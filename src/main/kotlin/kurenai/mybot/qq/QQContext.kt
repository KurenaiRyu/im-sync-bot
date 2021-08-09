package kurenai.mybot.qq

import kurenai.mybot.BotContext
import kurenai.mybot.handler.Handler
import kurenai.mybot.telegram.TelegramBotClient
import net.mamoe.mirai.event.Event

class QQContext(qqBotClient: QQBotClient, telegramBotClient: TelegramBotClient, val event: Event, var handler: Handler? = null) :
    BotContext(telegramBotClient, qqBotClient)