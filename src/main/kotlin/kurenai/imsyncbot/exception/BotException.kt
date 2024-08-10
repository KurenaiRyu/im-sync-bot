package kurenai.imsyncbot.exception

open class BotException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class CommandException(
    message: String,
    cause: Throwable? = null
) : BotException(message, cause)