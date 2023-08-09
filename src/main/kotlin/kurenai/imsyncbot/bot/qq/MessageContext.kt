package kurenai.imsyncbot.bot.qq

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.InputFileLocal
import it.tdlight.jni.TdApi.InputMessageAnimation
import it.tdlight.jni.TdApi.InputMessageContent
import it.tdlight.jni.TdApi.InputMessageDocument
import it.tdlight.jni.TdApi.InputMessagePhoto
import it.tdlight.jni.TdApi.InputMessageVideo
import it.tdlight.jni.TdApi.SendMessageAlbum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.service.FileService
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.BotUtil.formatUsername
import kurenai.imsyncbot.utils.TelegramUtil.asFmtText
import kurenai.imsyncbot.utils.TelegramUtil.escapeMarkdownChar
import kurenai.imsyncbot.utils.TelegramUtil.fmt
import kurenai.imsyncbot.utils.md5
import kurenai.imsyncbot.utils.withIO
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.GroupTempMessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.utils.MiraiExperimentalApi
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy
import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * 信息上下文
 * @author Kurenai
 * @since 9/2/2022 15:12:44
 */
sealed class MessageContext(
    val entity: QQMessage?,
    val bot: ImSyncBot
) {

    protected var tgMsgFormat =
        if (bot.configProperties.bot.tgMsgFormat.contains("\$msg")) bot.configProperties.bot.tgMsgFormat else "\$name: \$msg"

    /**
     * 格式化消息
     *
     * 字符串应经过markdown格式化，返回值已markdown格式化
     *
     * @param senderId
     * @param senderName
     * @return
     */
    protected fun String.formatMsg(senderId: Long, senderName: String? = null): String {
        return tgMsgFormat.escapeMarkdownChar().replace(BotUtil.NEWLINE_PATTERN.escapeMarkdownChar(), "\n", true)
            .replace(BotUtil.ID_PATTERN.escapeMarkdownChar(), senderId.toString(), true)
            .let {
                if (senderName?.isNotBlank() == true)
                    it.replace(BotUtil.NAME_PATTERN.escapeMarkdownChar(), senderName.escapeMarkdownChar(), true)
                else
                    it.replace(BotUtil.NAME_PATTERN.escapeMarkdownChar(), "", true)
            }.replace(BotUtil.MSG_PATTERN.escapeMarkdownChar(), this, true)
    }

    protected fun String.handleUrl(): String {
        return if (this.contains("b23.tv")) {
            this.replace("b23.tv", "b23.wtf")
        } else this
    }

    protected fun Image.shouldBeFile(): Boolean {
        val maxSide = maxOf(this.width, this.height)
        val minSide = minOf(this.width, this.height)
        val ratio: Float = maxSide / minSide.toFloat()
        return !this.isEmoji &&
                !((maxSide <= 1280 && ratio <= 20) || ratio <= MAX_IMAGE_RATIO)
    }
}

private val log = LogManager.getLogger()

private val json = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

@OptIn(ExperimentalXmlUtilApi::class)
private val xml = XML {
    encodeDefault = XmlSerializationPolicy.XmlEncodeDefault.NEVER
    unknownChildHandler = UnknownChildHandler { input, inputKind, descriptor, name, candidates ->
        emptyList()
    }
}

private const val MAX_IMAGE_RATIO = 2.7

class GroupMessageContext(
    entity: QQMessage?,
    bot: ImSyncBot,
    val event: GroupAwareMessageEvent,
    val group: Group,
    val messageChain: MessageChain,
    val senderId: Long = messageChain.source.fromId,
    val sender: NormalMember? = group[senderId],
    val senderName: String? = bot.userConfigService.idBindings[senderId]
        ?: bot.qq.qqBot.getFriend(senderId)?.remarkOrNick
        ?: sender?.remarkOrNameCardOrNick?.formatUsername()
) : MessageContext(entity, bot) {

    private lateinit var readyToSendMessage: ReadyToSendMessage
    private val isTempMessage = event is GroupTempMessageEvent
    val infoString by lazy { "[${group.name}(${this.group.id})]" }
    val simpleContent: String = messageChain.contentToString()
    val chatId: Long = if (isTempMessage) {
        bot.userConfigService.defaultChatId
    } else {
        bot.groupConfigService.qqTg[group.id] ?: bot.groupConfigService.defaultTgGroup
    }
    var replayToMessageId: Long = -1
    val normalType: Normal by lazy { Normal() }

    suspend fun getReplayToMessageId(): Long {
        if (replayToMessageId < 0) {
            replayToMessageId = messageChain[QuoteReply.Key]?.let {
                withIO {
                    MessageService.findTgIdByQQ(bot.qq.qqBot.id, it.source.targetId, it.source.ids.first())
                }
            }?.tgMsgId ?: 0
        }
        return replayToMessageId
    }

    fun getReadyToSendMessage() = if (::readyToSendMessage.isInitialized) readyToSendMessage
    else handleType(messageChain).also { readyToSendMessage = it }

    @OptIn(MiraiExperimentalApi::class)
    private fun handleType(messageChain: MessageChain): ReadyToSendMessage {
        return if (messageChain.contains(RichMessage.Key)) {
            val content = messageChain[RichMessage.Key]!!.content
            kotlin.runCatching {
                JsonReadyToSendMessage(json.decodeFromString(JsonMessageContent.serializer(), content))
            }.recoverCatching {
                val document = Jsoup.parse(content, Parser.xmlParser())
                val uuid = document.selectXpath("//image").attr("uuid")
                if (uuid.isNotBlank()) {
                    SingleImage(Image(uuid), true)
                } else {
                    XmlReadyToSendMessage(xml.decodeFromString(XmlMessageContent.serializer(), content))
                }
            }.recover { Normal() }.getOrThrow()
        } else if (messageChain.contains(Image.Key)) {
            val images = messageChain.filterIsInstance<Image>()
            if (images.size == 1) {
                val image = images.first()
                if (image.imageType == ImageType.GIF || image.imageType == ImageType.APNG || image.imageType == ImageType.UNKNOWN)
                    GifImage(image)
                else
                    SingleImage(image)
            } else
                MultiImage(images)
        } else if (messageChain.contains(FileMessage.Key)) {
            val fileMessage = messageChain[FileMessage.Key]!!
            val extension = fileMessage.name.substringAfterLast(".").lowercase()
            if (extension == "mkv" || extension == "mp4") {
                Video(fileMessage)
            } else {
                File(fileMessage)
            }
        } else if (messageChain.contains(ForwardMessage.Key)) {
            val forwardMessage = messageChain[ForwardMessage.Key]!!
            Forward(forwardMessage)
        } else {
            Normal()
        }
    }

    /**
     * 获取含有AT并且不含图片的消息内容，返回值已markdown格式化
     *
     * @return
     */
    private suspend fun getContentWithAtAndWithoutImage(): String {
        var lastAt: Long? = null
        var content = ""
        for (msg in messageChain) {
            if (msg is At) {
                val target = msg.target
                if (getReplayToMessageId() > 0 && target == group.bot.id) continue
                if (target == lastAt) continue
                else lastAt = target
                val id: Long?
                content += if (target == group.bot.id && getReplayToMessageId() <= 0) {
                    bot.userConfigService.masterUsername.ifBlank { bot.userConfigService.masterTg.toString() }.let {
                        "[${it.formatUsername().escapeMarkdownChar()}](tg://user?id=$it)"
                    }
                } else {
                    id = bot.userConfigService.links.find { it.qq == target }?.tg
                    val bindName = (bot.userConfigService.qqUsernames[target]
                        ?: bot.userConfigService.idBindings[target]).let { it ->
                        if (it.isNullOrBlank())
                            messageChain.bot.getFriend(target)?.remarkOrNick?.takeIf { it.isNotBlank() }
                                ?: group.getMember(target)?.remarkOrNameCardOrNick?.takeIf { it.isNotBlank() }
                                ?: target.toString()
                        else
                            it
                    }.formatUsername().escapeMarkdownChar()
                    "[$bindName](tg://user?id=$id)"
                }
            } else if (msg !is Image) {
                content += msg.contentToString().escapeMarkdownChar()
            }
        }
        return if (content.startsWith("\n")) content.substring(1)
        else content
    }

    sealed interface ReadyToSendMessage {
        suspend fun send(): Array<TdApi.Message>
    }

    inner class XmlReadyToSendMessage(val msg: XmlMessageContent) : ReadyToSendMessage {
        val url: String? = msg.url?.handleUrl()

        override suspend fun send(): Array<TdApi.Message> = arrayOf(
            bot.tg.sendMessageText(
                url?.escapeMarkdownChar()?.formatMsg(senderId, senderName)?.fmt() ?: simpleContent.asFmtText(),
                chatId,
                replayToMessageId = getReplayToMessageId(),
                untilPersistent = true
            )
        )
    }

    inner class JsonReadyToSendMessage(val msg: JsonMessageContent) : ReadyToSendMessage {
        val url: String? = (msg.meta.news?.jumpUrl ?: msg.meta.detail1.qqdocurl)?.handleUrl()

        override suspend fun send(): Array<TdApi.Message> = arrayOf(
            bot.tg.sendMessageText(
                url?.escapeMarkdownChar()?.formatMsg(senderId, senderName)?.fmt() ?: simpleContent.asFmtText(),
                chatId,
                replayToMessageId = getReplayToMessageId(),
                untilPersistent = true
            )
        )
    }

    inner class SingleImage(private val image: Image, private val onlyImage: Boolean = false) :
        ReadyToSendMessage {

        private val shouldBeFile: Boolean = image.shouldBeFile()

        override suspend fun send(): Array<TdApi.Message> {
            val func = TdApi.SendMessage().apply {
                this.chatId = this@GroupMessageContext.chatId
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }?.let { this.replyToMessageId = it }
                this.inputMessageContent = buildContent()
            }
            return arrayOf(bot.tg.execute(untilPersistent = true, function = func).also {
                CoroutineScope(bot.coroutineContext).launch {
                    FileService.cache(image, it)
                }
            })
        }

        private suspend fun buildContent(): InputMessageContent {
            val caption = if (onlyImage) {
                "".formatMsg(senderId, senderName)
            } else {
                getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
            }.fmt()
            val file = FileService.download(image)
            return if (shouldBeFile) {
                InputMessageDocument().apply {
                    this.caption = caption
                    document = file
                }
            } else {
                InputMessagePhoto().apply {
                    this.caption = caption
                    photo = file
                }
            }
        }


    }

    inner class MultiImage(val images: List<Image>) : ReadyToSendMessage {
        val shouldBeFile: Boolean = images.any {
            if (it.imageType == ImageType.GIF ||
                it.imageType == ImageType.APNG ||
                it.imageType == ImageType.UNKNOWN && !it.isEmoji
            )
                true
            else
                it.shouldBeFile()
        }

        override suspend fun send(): Array<TdApi.Message> {
            val formattedText = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName).fmt()
            val func = SendMessageAlbum().apply {
                this.chatId = this@GroupMessageContext.chatId
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }?.let { this.replyToMessageId = it }
                this.inputMessageContents = buildContents().also {
                    when (val last = it.last()) {
                        is InputMessageDocument -> {
                            last.caption = formattedText
                        }

                        is InputMessagePhoto -> {
                            last.caption = formattedText
                        }

                        else -> {}
                    }
                }
            }
            return bot.tg.execute(func, untilPersistent = true).messages.also {
                CoroutineScope(bot.coroutineContext).launch {
                    FileService.cache(images, it)
                }
            }
        }

        private suspend fun buildContents(): Array<InputMessageContent> {
            return FileService.download(images).map { file ->
                if (shouldBeFile) InputMessageDocument().apply {
                    this.document = file
                } else InputMessagePhoto().apply {
                    this.photo = file
                }
            }.toList().toTypedArray()
        }
    }

    inner class GifImage(
        val image: Image,
    ) : ReadyToSendMessage {

        override suspend fun send(): Array<TdApi.Message> {
            val func = TdApi.SendMessage().apply {
                this.chatId = this@GroupMessageContext.chatId
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }?.let { this.replyToMessageId = it }
                this.inputMessageContent = buildContent()
            }
            return arrayOf(bot.tg.execute(func, untilPersistent = true).also {
                CoroutineScope(bot.coroutineContext).launch {
                    FileService.cache(image, it)
                }
            })
        }

        private suspend fun buildContent(): InputMessageContent {
            getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
            val file = FileService.download(image)
            return InputMessageAnimation().apply {
                this.caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName).fmt()
                animation = file
            }
        }
    }

    inner class Video(val fileMessage: FileMessage) : ReadyToSendMessage {

        override suspend fun send(): Array<TdApi.Message> {
            val url = fileMessage.toAbsoluteFile(group)?.getUrl()
            require(url != null) { "获取视频地址失败" }
            val func = TdApi.SendMessage().apply {
                this.chatId = this@GroupMessageContext.chatId
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }?.let { this.replyToMessageId = it }
                this.inputMessageContent = InputMessageVideo().apply {
                    this.caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName).fmt()
                    this.video = InputFileLocal(BotUtil.downloadDoc(fileMessage.name, url).pathString)
                }
            }
            return arrayOf(bot.tg.execute(func, true))
        }
    }

    inner class File(
        val fileMessage: FileMessage,
    ) : ReadyToSendMessage {
        // 获取到的url telegram不接受, 貌似是文件名称问题
        // http://183.47.111.39/ftn_handler/B5C4BFA68F2C8362F222D540E5DE5CD40592321149BC8EE21CED5E4A516F2BED00DBA98D5E659DBCEBBC6662CAC8368F728482811326AF3BF8F9170BE2EE9C7C/?fname=31633433666234612D353338312D313165642D393662322D353235343030643663323236
        // Client request error: [400]Bad Request: wrong file identifier/HTTP URL specified

        override suspend fun send(): Array<TdApi.Message> {
            val url = getUrl()
            val func = TdApi.SendMessage().apply {
                this.chatId = this@GroupMessageContext.chatId
                this.inputMessageContent = InputMessageDocument().apply {
                    this.caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName).fmt()
                    this.document = InputFileLocal(BotUtil.downloadDoc(fileMessage.name, url).pathString)
                }
            }
            return arrayOf(bot.tg.execute(func, true))
        }

        private suspend fun getUrl(): String {
            val url = fileMessage.toAbsoluteFile(group)?.getUrl()
            log.debug("File message fetched url: $url")
            require(url != null) { "获取文件地址失败" }
            return url
        }
    }

    inner class Forward(val msg: ForwardMessage) : ReadyToSendMessage {
        val contextList = msg.nodeList.map {
            GroupMessageContext(
                null,
                bot,
                event,
                group,
                it.messageChain,
                senderId,
                senderName = "$senderName forward from ${it.senderName}"
            )
        }

        override suspend fun send(): Array<TdApi.Message> {
            return msg.nodeList.map {
                GroupMessageContext(
                    null,
                    bot,
                    event,
                    group,
                    it.messageChain,
                    senderId,
                    senderName = "$senderName forward from ${it.senderName}"
                )
            }.map {
                it.readyToSendMessage.send().toList()
            }.flatten().toTypedArray()
        }
    }

    inner class Normal : ReadyToSendMessage {

        override suspend fun send(): Array<TdApi.Message> = arrayOf(
            bot.tg.sendMessageText(
                getContentWithAtAndWithoutImage().formatMsg(senderId, senderName).fmt(),
                chatId,
                getReplayToMessageId(),
                untilPersistent = true
            )
        )
    }
}

//class PrivateMessageContext(
//    entity: QQMessage?,
//    bot: ImSyncBot,
//    val messageChain: MessageChain,
//    val chat: User,
//    val senderId: Long = chat.id,
//    val senderName: String = chat.remarkOrNick
//) : MessageContext(entity, bot) {
//
//    private var type: MessageType? = null
//    val infoString: String by lazy { "[${this.chat.remarkOrNick}(${this.chat.id})]" }
//    val simpleContent: String = messageChain.contentToString()
//    val chatId: String = (bot.userConfig.friendChatIds[chat.id] ?: bot.userConfig.defaultChatId).toString()
//    val replyId: Long? by lazy {
//        messageChain[QuoteReply.Key]?.let {
//            runBlocking(bot.coroutineContext) {
//                MessageService.findTgIdByQQ(bot.qq.qqBot.id, it.source.targetId, it.source.ids.first())
//            }
//        }?.tgMsgId
//    }
//    val hasReply: Boolean by lazy { replyId != null }
//    val normalType: Normal = Normal()
//
//    fun getType() = type ?: handleType(messageChain).also { type = it }
//
//    @OptIn(MiraiExperimentalApi::class)
//    private fun handleType(messageChain: MessageChain): MessageType {
//        return if (messageChain.contains(RichMessage.Key)) {
//            val content = messageChain[RichMessage.Key]!!.content
//            kotlin.runCatching {
//                JsonMessage(json.decodeFromString(JsonMessageContent.serializer(), content))
//            }.recoverCatching {
//                val document = Jsoup.parse(content, Parser.xmlParser())
//                val uuid = document.selectXpath("//image").attr("uuid")
//                if (uuid.isNotBlank()) {
//                    SingleImage(Image(uuid), true)
//                } else {
//                    XmlMessage(xml.decodeFromString(XmlMessageContent.serializer(), content))
//                }
//            }.recover { Normal() }.getOrThrow()
//        } else if (messageChain.contains(Image.Key)) {
//            val images = messageChain.filterIsInstance<Image>()
//            if (images.size == 1) {
//                val image = images.first()
//                if (image.imageType == ImageType.GIF || image.imageType == ImageType.APNG)
//                    GifImage(image)
//                else
//                    SingleImage(image)
//            } else
//                MultiImage(images)
//        } else {
//            Normal()
//        }
//    }
//
//    private fun getContentWithAtAndWithoutImage(): String {
//        var content = ""
//        for (msg in messageChain) {
//            if (msg !is Image) {
//                content += msg.contentToString().escapeMarkdownChar()
//            }
//        }
//        return if (content.startsWith("\n")) content.substring(1)
//        else content
//    }
//
//    sealed interface MessageType
//
//    inner class XmlMessage(val msg: XmlMessageContent) : MessageType {
//        val url: String? = msg.url?.handleUrl()
//        val telegramMessage: SendMessage = SendMessage(
//            chatId,
//            (url?.escapeMarkdownChar() ?: simpleContent).formatMsg(senderId, senderName)
//        ).apply {
//            parseMode = ParseMode.MARKDOWN_V2
//            replyId?.let { replyToMessageId = replyId }
//        }
//    }
//
//    inner class JsonMessage(val msg: JsonMessageContent) : MessageType {
//        val url: String? = (msg.meta.news?.jumpUrl ?: msg.meta.detail1.qqdocurl)?.handleUrl()
//        val telegramMessage: SendMessage = SendMessage(
//            chatId,
//            (url?.escapeMarkdownChar() ?: simpleContent).formatMsg(senderId, senderName)
//        ).apply {
//            parseMode = ParseMode.MARKDOWN_V2
//            replyId?.let { replyToMessageId = replyId }
//        }
//    }
//
//    inner class SingleImage(private val image: Image, private val onlyImage: Boolean = false) : MessageType {
//
//        private val shouldBeFile: Boolean = image.shouldBeFile()
//        private var inputFile: InputFile? = null
//        private var telegramMessage: Request<ResponseWrapper<Message>>? = null
//
//        suspend fun getTelegramMessage(): Request<ResponseWrapper<Message>> {
//            return telegramMessage ?: buildMessage()
//        }
//
//        suspend fun resolvedHttpUrlInvalidByModifyUrl(): Request<ResponseWrapper<Message>> {
//            val message = getTelegramMessage()
//            inputFile!!.attachName = inputFile!!.attachName.substringBefore("?")
//            return message
//        }
//
//        suspend fun resolvedHttpUrlInvalidByLocalDownload(): Request<ResponseWrapper<Message>> {
//            val message = getTelegramMessage()
//            val path = kotlin.runCatching {
//                BotUtil.downloadImg(inputFile!!.fileName!!, inputFile!!.attachName, overwrite = false)
//            }.recover {
//                val url = if (inputFile!!.attachName.endsWith("?term=2")) {
//                    inputFile!!.attachName.substringBefore("?")
//                } else {
//                    inputFile!!.attachName + "?term=2"
//                }
//                BotUtil.downloadImg(inputFile!!.fileName!!, url, overwrite = false)
//            }.getOrThrow()
//            inputFile!!.file = path.toFile()
//            inputFile!!.attachName = "attach://${path.fileName}"
//            return message
//        }
//
//        private suspend fun buildMessage(): Request<ResponseWrapper<Message>> {
//            return if (shouldBeFile) {
//                SendDocument(
//                    chatId,
//                    InputFile(image.queryUrl()).apply { fileName = image.imageId }.also { inputFile = it }).apply {
//                    parseMode = ParseMode.MARKDOWN_V2
//                    caption = if (onlyImage) {
//                        "".formatMsg(senderId, senderName)
//                    } else {
//                        getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
//                    }
//                    replyId?.let { replyToMessageId = replyId }
//                }
//            } else {
//                SendPhoto(
//                    chatId,
//                    InputFile(image.queryUrl()).apply { fileName = image.imageId }.also { inputFile = it }).apply {
//                    parseMode = ParseMode.MARKDOWN_V2
//                    caption = if (onlyImage) {
//                        "".formatMsg(senderId, senderName)
//                    } else {
//                        getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
//                    }
//                    replyId?.let { replyToMessageId = replyId }
//                }
//            }.also {
//                telegramMessage = it
//            }
//        }
//
//
//    }
//
//    inner class MultiImage(val images: List<Image>) : MessageType {
//        val shouldBeFile: Boolean = images.any {
//            if (it.imageType == ImageType.GIF ||
//                it.imageType == ImageType.APNG ||
//                it.imageType == ImageType.UNKNOWN && !it.isEmoji
//            )
//                true
//            else
//                it.shouldBeFile()
//        }
//
//        private val inputFiles = mutableListOf<InputFile>()
//        private var telegramMessage: SendMediaGroup? = null
//        private lateinit var inputMedias: List<InputMedia>
//
//        suspend fun getTelegramMessage(): SendMediaGroup {
//            return telegramMessage ?: buildTelegramMessage()
//        }
//
//        suspend fun resolvedHttpUrlInvalidByModifyUrl(): SendMediaGroup {
//            val message = getTelegramMessage()
//            inputFiles.forEach {
//                it.attachName = it.attachName.substringBefore("?")
//            }
//            return message
//        }
//
//        suspend fun resolvedHttpUrlInvalidByLocalDownload(): SendMediaGroup {
//            val message = getTelegramMessage()
//            inputFiles.forEach { inputFile ->
//                val path = kotlin.runCatching {
//                    BotUtil.downloadImg(inputFile.fileName!!, inputFile.attachName, overwrite = false)
//                }.recover {
//                    val url = if (inputFile.attachName.endsWith("?term=2")) {
//                        inputFile.attachName.substringBefore("?")
//                    } else {
//                        inputFile.attachName + "?term=2"
//                    }
//                    BotUtil.downloadImg(inputFile.fileName!!, url, overwrite = false)
//                }.getOrThrow()
//                inputFile.file = path.toFile()
//                inputFile.attachName = "attach://${path.fileName}"
//            }
//            return message
//        }
//
//        private suspend fun buildTelegramMessage(): SendMediaGroup {
//            inputFiles.clear()
//
//            inputMedias = images.mapNotNull {
//                if (it.imageType == ImageType.GIF) null
//                else if (shouldBeFile)
//                    InputMediaDocument(InputFile(it.queryUrl()).also(inputFiles::add)).apply {
//                        parseMode = ParseMode.MARKDOWN_V2
//                    }
//                else
//                    InputMediaPhoto(InputFile(it.queryUrl()).also(inputFiles::add)).apply {
//                        parseMode = ParseMode.MARKDOWN_V2
//                    }
//            }.also {
//                it[it.lastIndex].caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
//            }
//            inputMedias.windowed(10).forEach { sub ->
//                SendMediaGroup(chatId).apply {
//                    media = inputMedias
//                    replyId?.let { replyToMessageId = replyId }
//                }.also {
//                    telegramMessage = it
//                }
//            }
//            return SendMediaGroup(chatId).apply {
//                media = images.mapNotNull {
//                    if (it.imageType == ImageType.GIF) null
//                    else if (shouldBeFile) {
//                        InputMediaDocument(InputFile(it.queryUrl()).also(inputFiles::add)).apply {
//                            parseMode = ParseMode.MARKDOWN_V2
//                        }
//                    } else {
//                        InputMediaPhoto(InputFile(it.queryUrl()).also(inputFiles::add)).apply {
//                            parseMode = ParseMode.MARKDOWN_V2
//                        }
//                    }
//                }.also {
//                    it[it.lastIndex].caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
//                }
//                replyId?.let { replyToMessageId = replyId }
//            }.also {
//                telegramMessage = it
//            }
//        }
//    }
//
//    inner class GifImage(
//        val image: Image,
//    ) : MessageType {
//        suspend fun getTelegramMessage(): SendAnimation {
//            return SendAnimation(chatId, InputFile(image.queryUrl()).apply { fileName = image.imageId }).apply {
//                caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
//                parseMode = ParseMode.MARKDOWN_V2
//                replyId?.let { replyToMessageId = replyId }
//            }
//        }
//    }
//
//    inner class Forward(val msg: ForwardMessage) : MessageType {
//        val contextList = msg.nodeList.map {
//            PrivateMessageContext(
//                null,
//                bot,
//                it.messageChain,
//                chat,
//                it.senderId,
//                "$senderName forward from ${it.senderName}"
//            )
//        }
//    }
//
//    inner class Normal : MessageType {
//        val telegramMessage: SendMessage =
//            SendMessage(chatId, getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)).apply {
//                parseMode = ParseMode.MARKDOWN_V2
//                replyId?.let { replyToMessageId = replyId }
//            }
//    }
//}

@Serializable
data class XmlMessageContent(
    val action: String,
    val url: String?
)

@Serializable
data class JsonMessageContent(
    val app: String = "",
//    val config: Config = Config(),
    val desc: String = "",
//    val extra: Extra = Extra(),
    val meta: Meta = Meta(),
    val needShareCallBack: Boolean = false,
    val prompt: String = "",
    val ver: String = "",
    val view: String = ""
) {
    @Serializable
    data class Config(
        val autoSize: Int = 0,
        val ctime: Int = 0,
        val forward: Boolean = false,
        val height: Int = 0,
        val token: String = "",
        val type: String = "",
        val width: Int = 0
    )

    @Serializable
    data class Extra(
        @SerialName("app_type")
        val appType: Int = 0,
        val appid: Long = 0,
        val uin: Long = 0
    )

    @Serializable
    data class Meta(
        val news: News? = null,
        @SerialName("detail_1")
        val detail1: Detail1 = Detail1()
    ) {

        @Serializable
        data class News(
            val jumpUrl: String?
        )

        @Serializable
        data class Detail1(
            val appType: Int = 0,
            val appid: String = "",
            val desc: String = "",
            val gamePoints: String = "",
            val gamePointsUrl: String = "",
            val host: Host = Host(),
            val icon: String = "",
            val preview: String = "",
            val qqdocurl: String? = null,
            val scene: Int = 0,
            val shareTemplateData: ShareTemplateData = ShareTemplateData(),
            val shareTemplateId: String = "",
            val showLittleTail: String = "",
            val title: String = "",
            val url: String = ""
        ) {
            @Serializable
            data class Host(
                val nick: String = "",
                val uin: Int = 0
            )

            @Serializable
            class ShareTemplateData
        }
    }
}


