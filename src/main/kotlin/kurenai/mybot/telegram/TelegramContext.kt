package kurenai.mybot.telegram

import kurenai.mybot.BotContext
import kurenai.mybot.qq.QQBotClient
import org.telegram.telegrambots.meta.api.objects.Update

class TelegramContext(telegramBotClient: TelegramBotClient, qqBotClient: QQBotClient, val update: Update) :
    BotContext(telegramBotClient, qqBotClient)