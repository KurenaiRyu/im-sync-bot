package kurenai.imsyncbot

import kurenai.imsyncbot.bot.telegram.TelegramBot

/**
 * @author Kurenai
 * @since 2023/6/22 23:25
 */

sealed interface BotStatus

object Initializing : BotStatus
object Running : BotStatus
object Stopped : BotStatus