package kurenai.imsyncbot.exception

class ImSyncBotRuntimeException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)