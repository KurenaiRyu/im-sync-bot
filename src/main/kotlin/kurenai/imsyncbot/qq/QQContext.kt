package kurenai.imsyncbot.qq

import kurenai.imsyncbot.BotContext
import kurenai.imsyncbot.handler.qq.QQHandler
import kurenai.imsyncbot.telegram.TelegramBotClient
import net.mamoe.mirai.event.Event

class QQContext(
    qqBotClient: QQBotClient,
    telegramBotClient: TelegramBotClient,
    val event: Event,
    var handler: QQHandler,
) : BotContext(telegramBotClient, qqBotClient)