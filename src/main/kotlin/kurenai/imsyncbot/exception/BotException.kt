package kurenai.imsyncbot.exception

class BotException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)