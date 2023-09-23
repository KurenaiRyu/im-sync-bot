package kurenai.imsyncbot.utils

import it.tdlight.jni.TdApi.*
import kotlinx.coroutines.runBlocking
import kurenai.imsyncbot.bot.telegram.defaultTelegramBot
import kurenai.imsyncbot.utils.TelegramUtil.messageId
import kurenai.imsyncbot.utils.TelegramUtil.replyToMessageId

/**
 * @author Kurenai
 * @since 2023/3/2 4:19
 */
object TelegramUtil {

    private val escapeChar = "_*[]()~`>#+-=|{}.!".toCharArray()

    fun String.escapeMarkdownChar(): String {
        var result = this
        for (c in escapeChar) {
            result = result.replace(c.toString(), "\\$c")
        }
        return result
    }


    /**
     * 获取格式化文本对象，不会对字符串进行markdown等格式化操作
     *
     * @param parseMode
     * @return
     */
    fun String.fmt(parseMode: ParseMode = ParseMode.MARKDOWN_V2): FormattedText = parseMode.ins?.let {
        runBlocking {
            defaultTelegramBot.execute(ParseTextEntities(this@fmt, it))
        }
    } ?: this.asFmtText()

    fun String.asFmtText() = FormattedText().apply { text = this@asFmtText }

    fun TextEntity.text(text: String) = text.substring(this.offset, this.length)

    fun messageDocument(chatId: Long, url: String, msg: FormattedText) = SendMessage().apply {
        this.chatId = chatId
        inputMessageContent = InputMessageDocument().apply {
            this.caption = msg
            this.document = InputFileGenerated().apply {
                originalPath = url
            }
        }
    }

    fun messagePhoto(chatId: Long, photoPath: String, msg: FormattedText) = SendMessage().apply {
        this.chatId = chatId
        inputMessageContent = InputMessagePhoto().apply {
            this.caption = msg
            this.photo = InputFileLocal().apply {
                path = photoPath
            }
        }
    }

    fun messageAlbumPhoto(chatId: Long, photos: Map<String, String>) = SendMessageAlbum().apply {
        this.chatId = chatId
        this.inputMessageContents = photos.map { (url, msg) ->
            InputMessagePhoto().apply {
                this.caption = msg.asFmtText()
                this.photo = InputFileLocal().apply {
                    path = url
                }
            }
        }.toTypedArray()
    }

    fun messageAlbumPhoto(chatId: Long, pairs: List<Pair<String, String>>) = SendMessageAlbum().apply {
        this.chatId = chatId
        this.inputMessageContents = pairs.map { (url, msg) ->
            InputMessagePhoto().apply {
                this.caption = msg.asFmtText()
                this.photo = InputFileGenerated().apply {
                    originalPath = url
                }
            }
        }.toTypedArray()
    }

    fun answerInlineQuery(id: Long, inlineQueryResults: Array<InputInlineQueryResult>, cacheTime: Int = 60) =
        AnswerInlineQuery().apply {
            this.inlineQueryId = id
            this.results = inlineQueryResults
            this.cacheTime = cacheTime
        }

    fun answerInlineQueryEmpty(id: Long, cacheTime: Int = 60) = AnswerInlineQuery().apply {
        this.inlineQueryId = id
        this.results = emptyArray()
        this.cacheTime = cacheTime
        this.button = InlineQueryResultsButton().apply {
            this.text = "搜索结果为空"
            this.type = InlineQueryResultsButtonTypeStartBot().apply {
                parameter = "help"
            }
        }
    }

    fun messageText(formattedText: FormattedText, chatId: Long) = SendMessage().apply {
        this.chatId = chatId
        inputMessageContent = InputMessageText().apply {
            this.text = formattedText
        }
    }

    fun Message.sendUserId() = when (val sender = this.senderId) {
        is MessageSenderChat -> sender.chatId
        is MessageSenderUser -> sender.userId
        else -> 0
    }

    fun Message.userSender(): MessageSenderUser? = this.senderId as? MessageSenderUser

    fun MessageContent.file() = when (this) {
        is MessageAnimation -> this.animation.animation
        is MessageAudio -> this.audio.audio
        is MessageDocument -> this.document.document
        is MessagePhoto -> this.photo.sizes.maxBy { it.photo.size }.photo
        is MessageSticker -> this.sticker.sticker
        is MessageVideo -> this.video.video
        else -> null
    }

    fun MessageContent.textOrCaption() = when (this) {
        is MessageAnimation -> this.caption
        is MessageAudio -> this.caption
        is MessageDocument -> this.caption
        is MessagePhoto -> this.caption
        is MessageVideo -> this.caption
        is MessageText -> this.text
        else -> null
    }

    fun User.username() = this.usernames.activeUsernames.firstOrNull() ?: this.id.toString()

    fun User.isBot() = this.type.constructor == UserTypeBot.CONSTRUCTOR

    fun Update.idString() = when (this) {
        is UpdateNewMessage -> {
            "${this::class.simpleName}[${this.message.chatId}:${this.message.id}]"
        }

        is UpdateMessageContent -> {
            "${this::class.simpleName}[${this.chatId}:${this.messageId}]"
        }

        is UpdateNewInlineQuery -> {
            "${this::class.simpleName}[${this.id}]"
        }

        is UpdateMessageEdited -> {
            "${this::class.simpleName}[${this.chatId}:${this.messageId}]"
        }

        else -> {
            this::class.simpleName ?: "UnknownUpdateType"
        }
    }

    infix fun <T : Object> T.constructorEquals(constructor: Int) = this.constructor == constructor

    /**
     * 获取简短的描述
     */
    fun Update.info(): String {
        return when (this) {
            is UpdateConnectionState -> "Update connection state: ${state::class.simpleName}"
            is UpdateMessageSendFailed -> "Sent message $oldMessageId -> ${message.id} to chat ${message.chatId} fail: $errorCode $errorMessage"
            is UpdateMessageSendSucceeded -> "Sent message $oldMessageId -> ${message.id} to chat ${message.chatId}"
            is UpdateDeleteMessages -> "Deleted messages $messageIds from chat $chatId"
            is UpdateMessageContent -> "Edited message content $messageId from chat $chatId"
            is UpdateMessageEdited -> "Edited message $messageId from chat $chatId"
            is UpdateNewInlineQuery -> "New inline query (${this.id})[${query}] from user ${senderUserId}, offset $offset"
            is UpdateNewMessage -> {
                if (message.isOutgoing) {
                    "New message(out going) ${message.id} from chat ${message.chatId}"
                } else {
                    "New message ${message.id} from chat ${message.chatId}"
                }
            }

            else -> this::class.simpleName ?: "Unknown Update"
        }
    }

    //////////////  message reply  //////////////
    fun SendMessage.setReplyToMessageId(messageId: Long?, chatId: Long? = null) =
        this.replyTo.setMessageId(messageId, chatId)

    fun Message.replyToMessageId() = this.replyTo.messageId()
    fun Message.replyInChatId() = this.replyTo.chatId()

    fun MessageReplyTo.setMessageId(messageId: Long?, chatId: Long? = null) = (this as? MessageReplyToMessage)?.apply {
        messageId?.also(this::messageId::set)
        chatId?.also(this::chatId::set)
    }

    fun MessageReplyTo.messageId() = (this as? MessageReplyToMessage)?.messageId
    fun MessageReplyTo.chatId() = (this as? MessageReplyToMessage)?.chatId
}

enum class ParseMode(val ins: TextParseMode?) {
    TEXT(null),
    MARKDOWN(TextParseModeMarkdown()),
    MARKDOWN_V2(TextParseModeMarkdown(2)),
    HTML(TextParseModeHTML()),
}
