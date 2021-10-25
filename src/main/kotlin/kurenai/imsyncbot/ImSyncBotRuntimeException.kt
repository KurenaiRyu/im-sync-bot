package kurenai.imsyncbot

class ImSyncBotRuntimeException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)