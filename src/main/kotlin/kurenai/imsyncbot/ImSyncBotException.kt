package kurenai.imsyncbot

class ImSyncBotException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)