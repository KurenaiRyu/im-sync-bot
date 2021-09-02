package kurenai.imsyncbot.telegram

import kurenai.imsyncbot.BotContext
import kurenai.imsyncbot.qq.QQBotClient
import org.telegram.telegrambots.meta.api.objects.Update

class TelegramContext(telegramBotClient: TelegramBotClient, qqBotClient: QQBotClient, val update: Update) :
    BotContext(telegramBotClient, qqBotClient)