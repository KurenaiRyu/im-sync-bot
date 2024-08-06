package kurenai.imsyncbot.bot.qq

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.*
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
import kurenai.imsyncbot.utils.*
import kurenai.imsyncbot.utils.BotUtil.formatUsername
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.GroupTempMessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.MiraiExperimentalApi
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy
import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.nio.file.Path
import kotlin.io.path.fileSize
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

    private var tgMsgFormat =
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
        val formatName = if (senderName?.isNotBlank() == true) senderName else "BLANK_NAME"
        return "`${formatName.escapeMarkdown()}` \\#id$senderId\n\n$this"

//        return tgMsgFormat.escapeMarkdown().replace(BotUtil.NEWLINE_PATTERN.escapeMarkdown(), "\n", true)
//            .replace(BotUtil.ID_PATTERN.escapeMarkdown(), senderId.toString(), true)
//            .let {
//                if (senderName?.isNotBlank() == true)
//                    it.replace(
//                        BotUtil.NAME_PATTERN.escapeMarkdown(),
//                        senderName.replace('-', '_').escapeMarkdown(),
//                        true
//                    )
//                else
//                    it.replace(BotUtil.NAME_PATTERN.escapeMarkdown(), "", true)
//            }.replace(BotUtil.MSG_PATTERN.escapeMarkdown(), this, true)
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
    private var _replayToMessageId: Long = -1

    val infoString by lazy { "[${group.name}(${this.group.id})]" }
    val simpleContent: String = messageChain.contentToString()
    val chatId: Long = if (isTempMessage) {
        bot.userConfigService.defaultChatId
    } else {
        bot.groupConfigService.qqTg[group.id] ?: bot.groupConfigService.defaultTgGroup
    }
    val normalType: Normal by lazy { Normal() }

    suspend fun getReplayToMessageId(): Long {
        if (_replayToMessageId < 0) {
            _replayToMessageId = messageChain[QuoteReply.Key]?.let {
                withIO {
                    MessageService.findTgIdByQQ(bot.qq.qqBot.id, it.source.targetId, it.source.ids.first())
                }
            }?.tgMsgId ?: 0
        }
        return _replayToMessageId
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
        } else if (messageChain.contains(OnlineShortVideo.Key)) {
            ShortVideo(messageChain[OnlineShortVideo.Key]!!)
        } else if (messageChain.contains(Image.Key)) {
            val images = messageChain.filterIsInstance<Image>()
            if (images.size == 1) {
                val image = images.first()
                if (image.imageType == ImageType.GIF ||
                    image.imageType == ImageType.APNG ||
                    (image.imageType == ImageType.UNKNOWN && image.isEmoji)
                )
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
                        "[${it.formatUsername().escapeMarkdown()}](tg://user?id=$it)"
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
                    }.formatUsername().escapeMarkdown()
                    "[$bindName](tg://user?id=$id)"
                }
            } else if (msg !is Image) {
                content += msg.contentToString().escapeMarkdown()
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
                url?.escapeMarkdown()?.formatMsg(senderId, senderName)?.fmt() ?: simpleContent.asFmtText(),
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
                url?.escapeMarkdown()?.formatMsg(senderId, senderName)?.fmt() ?: simpleContent.asFmtText(),
                chatId,
                replayToMessageId = getReplayToMessageId(),
                untilPersistent = true
            )
        )
    }

    inner class SingleImage(private val image: Image, private val onlyImage: Boolean = false) : ReadyToSendMessage {

        private val shouldBeFile: Boolean = image.shouldBeFile()
        private var fileSize: Long = 0L

        override suspend fun send(): Array<TdApi.Message> {
            val inputFile = FileService.download(image)


            return if (inputFile is InputFileLocal) {
//                val f = bot.tg.send(PreliminaryUploadFile().apply {
//                    this.file = inputFile
//                    this.fileType = if (shouldBeFile) FileTypeDocument() else FileTypePhoto()
//                    this.priority = 1
//                })
//                val deferred = bot.tg.messageHandler.addListener(matchBlock = { event ->
//                    event is UpdateFile && event.file.id == f.id && event.file.remote.isUploadingCompleted
//                }) { event: UpdateFile ->
//                    TgMessageHandler.ListenerResult.complete(event.file)
//                }
                runCatching {
                    fileSize = Path.of(inputFile.path).fileSize()
                    sendFileMessage(inputFile)
                }.recover { ex ->
                    throw ex
//                    log.warn("Send image failed", ex)
//
//                    val messages =
//                        sendMessage(InputFileRemote("AgACAv7___8dBEqDOiEAAQGUXWUpby1uzGVg8oBeQUASzpcAAfjmzgACj70xG3SWSFZfHeKh_spbbgEAAwIAA2kAAzAE"))
//                    val message = messages[0]
//                    CoroutineScope(bot.qq.coroutineContext).launch {
//                        runCatching {
//                            log.debug("Wait for upload file...")
//                            val file = deferred.await()
//                            log.debug("Uploaded file: {}", file)
//                            bot.tg.send(EditMessageMedia().apply {
//                                this.chatId = message.chatId
//                                this.messageId = message.id
//                                this.inputMessageContent = buildContent(InputFileRemote(file.remote.id))
//                            })
//                        }.onFailure { ex ->
//                            log.warn("Send photo fail: {}", ex.message, ex)
//                            normalType.send()
//                        }
//                    }
//                    messages
                }.getOrThrow()
            } else {
                sendFileMessage(inputFile)
            }
        }

        private suspend fun sendFileMessage(inputFile: InputFile): Array<TdApi.Message> {
            val func = SendMessage().apply {
                this.chatId = this@GroupMessageContext.chatId
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }?.let { this.setReplyToMessageId(it) }
                this.inputMessageContent = buildFileMessageContent(inputFile)
            }
            return arrayOf(bot.tg.send(untilPersistent = true, function = func).also {
                CoroutineScope(bot.coroutineContext).launch {
                    FileService.cacheEmoji(image, it)
                }
            })
        }

        private suspend fun buildFileMessageContent(file: InputFile): InputMessageContent {
            val caption = if (onlyImage) {
                "".formatMsg(senderId, senderName)
            } else {
                getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
            }.fmt()
            return if (shouldBeFile && fileSize > 300 * 1024) {
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
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }?.let { this.setReplyToMessageId(it) }
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
            return bot.tg.send(func, untilPersistent = true).messages.also {
                CoroutineScope(bot.coroutineContext).launch {
                    FileService.cacheEmoji(images, it)
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
            val func = SendMessage().apply {
                this.chatId = this@GroupMessageContext.chatId
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }?.let { this.setReplyToMessageId(it) }
                this.inputMessageContent = buildContent()
            }
            return arrayOf(bot.tg.send(func, untilPersistent = true).also {
                CoroutineScope(bot.coroutineContext).launch {
                    FileService.cacheEmoji(image, it)
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

    inner class ShortVideo(val shortVideo: OnlineShortVideo) : ReadyToSendMessage {
        override suspend fun send(): Array<TdApi.Message> {
            val name = "${shortVideo.fileMd5.toHex()}.mp4"
            val url = shortVideo.urlForDownload
            val func = SendMessage().apply {
                this.chatId = this@GroupMessageContext.chatId
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }?.let { this.setReplyToMessageId(it) }
                this.inputMessageContent = InputMessageVideo().apply {
                    this.caption = "${shortVideo.filename}.${shortVideo.fileFormat}"
                        .escapeMarkdown().formatMsg(senderId, senderName).fmt()
                    this.video = InputFileLocal(BotUtil.downloadDoc(name, url).pathString)
                }
            }
            return arrayOf(bot.tg.send(func, true))
        }
    }

    inner class Video(val fileMessage: FileMessage) : ReadyToSendMessage {

        override suspend fun send(): Array<TdApi.Message> {
            val url = fileMessage.toAbsoluteFile(group)?.getUrl()
            require(url != null) { "获取视频地址失败" }
            val func = SendMessage().apply {
                this.chatId = this@GroupMessageContext.chatId
                this@GroupMessageContext.getReplayToMessageId().takeIf { it > 0 }?.let { this.setReplyToMessageId(it) }
                this.inputMessageContent = InputMessageVideo().apply {
                    this.caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName).fmt()
                    this.video = InputFileLocal(BotUtil.downloadDoc(fileMessage.name, url).pathString)
                }
            }
            return arrayOf(bot.tg.send(func, true))
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
            val func = SendMessage().apply {
                this.chatId = this@GroupMessageContext.chatId
                this.inputMessageContent = InputMessageDocument().apply {
                    this.caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName).fmt()
                    this.document = InputFileLocal(BotUtil.downloadDoc(fileMessage.name, url).pathString)
                }
            }
            return arrayOf(bot.tg.send(func, true))
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


