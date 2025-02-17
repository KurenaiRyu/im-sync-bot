package kurenai.imsyncbot

/**
 * @author Kurenai
 * @since 2023/6/22 23:25
 */

sealed interface BotStatus

data object Initializing : BotStatus
data object Running : BotStatus
data object Stopped : BotStatus