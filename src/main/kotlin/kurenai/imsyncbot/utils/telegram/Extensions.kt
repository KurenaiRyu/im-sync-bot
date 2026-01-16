package kurenai.imsyncbot.utils.telegram

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.*

//region reply
var TdApi.Function<*>.replyToMessageId: Long
    get() = when (this) {
        is SendMessage -> {
            this.replyTo.messageId
        }

        is SendMessageAlbum -> {
            this.replyTo.messageId
        }

        else -> 0
    }
    set(id) = run {
        val replyTo = InputMessageReplyToMessage().apply {
            this.messageId = id
        }
        when (this) {
            is SendMessage -> {
                this.replyTo = replyTo
            }

            is SendMessageAlbum -> {
                this.replyTo = replyTo
            }
        }
    }

val InputMessageReplyTo.messageId: Long
    get() = (this as? InputMessageReplyToMessage)?.messageId ?: 0

val MessageReplyTo.messageId: Long
    get() = (this as? MessageReplyToMessage)?.messageId ?: 0

val MessageReplyTo.chatId: Long
    get() = (this as? MessageReplyToMessage)?.chatId ?: 0
//endregion