package kurenai.imsyncbot.handler.qq

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.callback.impl.GetFileUrlCallback.Companion.METHOD
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.BotUtil.formatUsername
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.ResponseWrapper
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardButton
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardMarkup
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.request.Request
import moe.kurenai.tdlight.request.message.*
import moe.kurenai.tdlight.util.MarkdownUtil.fm2md
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.MiraiExperimentalApi
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy
import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * 信息上下文
 * @author Kurenai
 * @since 9/2/2022 15:12:44
 */
sealed interface MessageContext

private val log = LogManager.getLogger()

data class GroupMessageContext(
    val bot: ImSyncBot,
    val group: Group,
    val messageChain: MessageChain,
    val senderId: Long = messageChain.source.fromId,
    val sender: NormalMember? = group[senderId],
    val senderName: String? =
        senderId
            .let { bot.userConfig.idBindings[it] ?: bot.qq.qqBot.getFriend(it)?.remarkOrNick } ?: sender?.remarkOrNameCardOrNick
            ?.formatUsername()
) : MessageContext {

    private var tgMsgFormat = if (bot.configProperties.handler.tgMsgFormat.contains("\$msg")) bot.configProperties.handler.tgMsgFormat else "\$name: \$msg"
    private var type: MessageType? = null

    val simpleContent: String = messageChain.contentToString()
    val chatId: String = (bot.groupConfig.qqTg[group.id] ?: bot.groupConfig.defaultTgGroup).toString()
    val replyId: Int? by lazy {
        messageChain[QuoteReply.Key]?.let {
            runBlocking(bot.coroutineContext) {
                CacheService.getTgIdByQQ(
                    it.source.targetId,
                    it.source.ids[0]
                )
            }
        }?.second
    }
    val hasReply: Boolean by lazy { replyId != null }
    val normalType: Normal = Normal()

    fun getType() = type ?: handleType(messageChain).also { type = it }

    @OptIn(MiraiExperimentalApi::class)
    private fun handleType(messageChain: MessageChain): MessageType {
        return if (messageChain.contains(RichMessage.Key)) {
            val content = messageChain[RichMessage.Key]!!.content
            kotlin.runCatching {
                JsonMessage(json.decodeFromString(JsonMessageContent.serializer(), content))
            }.recoverCatching {
                val document = Jsoup.parse(content, Parser.xmlParser())
                val uuid = document.selectXpath("//image").attr("uuid")
                if (uuid.isNotBlank()) {
                    SingleImage(Image(uuid), true)
                } else {
                    XmlMessage(xml.decodeFromString(XmlMessageContent.serializer(), content))
                }
            }.recover { Normal() }.getOrThrow()
        } else if (messageChain.contains(Image.Key)) {
            val images = messageChain.filterIsInstance<Image>()
            if (images.size == 1) {
                val image = images.first()
                if (image.imageType == ImageType.GIF || image.imageType == ImageType.APNG)
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

    private fun getContentWithAtAndWithoutImage(): String {
        var lastAt: Long? = null
        var content = ""
        for (msg in messageChain) {
            if (msg is At) {
                val target = msg.target
                if (hasReply && target == group.bot.id) continue
                if (target == lastAt) continue
                else lastAt = target
                val id: Long?
                content += if (target == group.bot.id && !hasReply) {
                    id = bot.userConfig.masterTg
                    bot.userConfig.masterUsername.ifBlank { id.toString() }.let {
                        "[${it.formatUsername().fm2md()}](tg://user?id=$id)"
                    }
                } else {
                    id = bot.userConfig.links.find { it.qq == target }?.tg
                    val bindName = (bot.userConfig.qqUsernames[target] ?: bot.userConfig.idBindings[target]).let {
                        if (it.isNullOrBlank())
                            messageChain.bot.getFriend(target)?.remarkOrNick?.takeIf { it.isNotBlank() }
                                ?: group.getMember(target)?.remarkOrNameCardOrNick?.takeIf { it.isNotBlank() }
                                ?: target.toString()
                        else
                            it
                    }.formatUsername().fm2md()
                    "[$bindName](tg://user?id=$id)"
                }
            } else if (msg !is Image) {
                content += msg.contentToString().fm2md()
            }
        }
        return if (content.startsWith("\n")) content.substring(1)
        else content
    }

    /**
     * 格式化消息
     *
     * 字符串应经过markdown格式化，返回值已markdown格式化
     *
     * @param senderId
     * @param senderName
     * @return
     */
    private fun String.formatMsg(senderId: Long, senderName: String? = null): String {
        return tgMsgFormat.fm2md().replace(BotUtil.NEWLINE_PATTERN.fm2md(), "\n", true)
            .replace(BotUtil.ID_PATTERN.fm2md(), senderId.toString(), true)
            .let {
                if (senderName?.isNotBlank() == true)
                    it.replace(BotUtil.NAME_PATTERN.fm2md(), senderName.fm2md(), true)
                else
                    it.replace(BotUtil.NAME_PATTERN.fm2md(), "", true)
            }.replace(BotUtil.MSG_PATTERN.fm2md(), this, true)
    }

    fun String.handleUrl(): String {
        return if (this.contains("b23.tv")) {
            this.replace("b23.tv", "b23.wtf")
        } else this
    }

    private fun Image.shouldBeFile(): Boolean {
        val maxSide = maxOf(this.width, this.height)
        val minSide = minOf(this.width, this.height)
        val ratio: Float = maxSide / minSide.toFloat()
        return !this.isEmoji &&
                !((maxSide <= 1280 && ratio <= 20) || ratio <= MAX_IMAGE_RATIO)
    }

    companion object {

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
    }

    sealed interface MessageType

    inner class XmlMessage(val msg: XmlMessageContent) : MessageType {
        val url: String? = msg.url?.handleUrl()
        val telegramMessage: SendMessage = SendMessage(
            chatId,
            (url?.fm2md() ?: simpleContent).formatMsg(senderId, senderName)
        ).apply {
            parseMode = ParseMode.MARKDOWN_V2
            replyId?.let { replyToMessageId = replyId }
        }
    }

    inner class JsonMessage(val msg: JsonMessageContent) : MessageType {
        val url: String? = (msg.meta.news?.jumpUrl ?: msg.meta.detail1.qqdocurl)?.handleUrl()
        val telegramMessage: SendMessage = SendMessage(
            chatId,
            (url?.fm2md() ?: simpleContent).formatMsg(senderId, senderName)
        ).apply {
            parseMode = ParseMode.MARKDOWN_V2
            replyId?.let { replyToMessageId = replyId }
        }
    }

    inner class SingleImage(private val image: Image, private val onlyImage: Boolean = false) : MessageType {

        private val shouldBeFile: Boolean = image.shouldBeFile()
        private var inputFile: InputFile? = null
        private var telegramMessage: Request<ResponseWrapper<Message>>? = null

        suspend fun getTelegramMessage(): Request<ResponseWrapper<Message>> {
            return telegramMessage ?: buildMessage()
        }

        suspend fun resolvedHttpUrlInvalid(): Request<ResponseWrapper<Message>> {
            val message = getTelegramMessage()
            val path = BotUtil.downloadImg(inputFile!!.fileName!!, inputFile!!.attachName)
            inputFile!!.file = path.toFile()
            inputFile!!.attachName = "attach://${path.fileName}"
            return message
        }

        private suspend fun buildMessage(): Request<ResponseWrapper<Message>> {
            return if (shouldBeFile) {
                SendDocument(chatId, InputFile(image.queryUrl()).apply { fileName = image.imageId }.also { inputFile = it }).apply {
                    parseMode = ParseMode.MARKDOWN_V2
                    caption = if (onlyImage) {
                        "".formatMsg(senderId, senderName)
                    } else {
                        getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                    }
                    replyId?.let { replyToMessageId = replyId }
                }
            } else {
                SendPhoto(chatId, InputFile(image.queryUrl()).apply { fileName = image.imageId }.also { inputFile = it }).apply {
                    parseMode = ParseMode.MARKDOWN_V2
                    caption = if (onlyImage) {
                        "".formatMsg(senderId, senderName)
                    } else {
                        getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                    }
                    replyId?.let { replyToMessageId = replyId }
                }
            }.also {
                telegramMessage = it
            }
        }


    }

    inner class MultiImage(val images: List<Image>) : MessageType {
        val shouldBeFile: Boolean = images.any {
            if (it.imageType == ImageType.GIF ||
                it.imageType == ImageType.APNG ||
                it.imageType == ImageType.UNKNOWN && !it.isEmoji
            )
                true
            else
                it.shouldBeFile()
        }

        private val inputFiles = mutableListOf<InputFile>()
        private var telegramMessage: SendMediaGroup? = null

        suspend fun getTelegramMessage(): SendMediaGroup {
            return telegramMessage ?: buildTelegramMessage()
        }

        suspend fun resolvedHttpUrlInvalid(): SendMediaGroup {
            val message = getTelegramMessage()
            inputFiles.forEach {
                val path = BotUtil.downloadImg(it.fileName!!, it.attachName)
                it.file = path.toFile()
                it.attachName = "attach://${path.fileName}"
            }
            return message
        }

        private suspend fun buildTelegramMessage(): SendMediaGroup {
            inputFiles.clear()
            return SendMediaGroup(chatId).apply {
                media = images.mapNotNull {
                    if (it.imageType == ImageType.GIF) null
                    else if (shouldBeFile)
                        InputMediaDocument(InputFile(it.queryUrl()).also(inputFiles::add)).apply {
                            parseMode = ParseMode.MARKDOWN_V2
                        }
                    else
                        InputMediaPhoto(InputFile(it.queryUrl()).also(inputFiles::add)).apply {
                            parseMode = ParseMode.MARKDOWN_V2
                        }
                }.also {
                    it[it.lastIndex].caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                }
                replyId?.let { replyToMessageId = replyId }
            }.also {
                telegramMessage = it
            }
        }
    }

    inner class GifImage(
        val image: Image,
    ) : MessageType {
        suspend fun getTelegramMessage(): SendAnimation {
            return SendAnimation(chatId, InputFile(image.queryUrl()).apply { fileName = image.imageId }).apply {
                caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                parseMode = ParseMode.MARKDOWN_V2
                replyId?.let { replyToMessageId = replyId }
            }
        }
    }

    inner class Video(val fileMessage: FileMessage) : MessageType {
        suspend fun getTelegramMessage(): SendVideo {
            val url = fileMessage.toAbsoluteFile(group)?.getUrl()
            require(url != null) { "获取视频地址失败" }
            return SendVideo(chatId, InputFile(url).apply { fileName = fileMessage.name }).apply {
                caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                parseMode = ParseMode.MARKDOWN_V2
                replyId?.let { replyToMessageId = replyId }
            }
        }
    }

    inner class File(
        val fileMessage: FileMessage,
    ) : MessageType {
        // 获取到的url telegram不接受, 貌似是文件名称问题
        // http://183.47.111.39/ftn_handler/B5C4BFA68F2C8362F222D540E5DE5CD40592321149BC8EE21CED5E4A516F2BED00DBA98D5E659DBCEBBC6662CAC8368F728482811326AF3BF8F9170BE2EE9C7C/?fname=31633433666234612D353338312D313165642D393662322D353235343030643663323236
        // Client request error: [400]Bad Request: wrong file identifier/HTTP URL specified
        val shouldBeFile = false

        suspend fun getFileMessage(): SendDocument {
            return SendDocument(chatId, InputFile(getUrl()).apply {
                fileName = fileMessage.name
            }).apply {
                caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                parseMode = ParseMode.MARKDOWN_V2
                replyId?.let { replyToMessageId = replyId }
            }
        }

        suspend fun getTextMessage(): SendMessage {
            return SendMessage(chatId, getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)).apply {
                parseMode = ParseMode.MARKDOWN_V2
                replyId?.let { replyToMessageId = replyId }
                replyMarkup = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("获取/刷新下载链接").apply {
                    callbackData = "$METHOD ${group.id} ${fileMessage.toAbsoluteFile(group)?.absolutePath ?: ""}"
                })))
            }
        }

        private suspend fun getUrl(): String {
            val url = fileMessage.toAbsoluteFile(group)?.getUrl()
            log.debug("File message fetched url: $url")
            require(url != null) { "获取文件地址失败" }
            return url
        }
    }

    inner class Forward(val msg: ForwardMessage) : MessageType {
        val contextList = msg.nodeList.map { GroupMessageContext(bot, group, it.messageChain, senderId, senderName = "$senderName forward from ${it.senderName}") }
    }

    inner class Normal : MessageType {
        val telegramMessage: SendMessage = SendMessage(chatId, getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)).apply {
            parseMode = ParseMode.MARKDOWN_V2
            replyId?.let { replyToMessageId = replyId }
        }
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
    val config: Config = Config(),
    val desc: String = "",
    val extra: Extra = Extra(),
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
        val forward: Int = 0,
        val height: Int = 0,
        val token: String = "",
        val type: String = "",
        val width: Int = 0
    )

    @Serializable
    data class Extra(
        @SerialName("app_type")
        val appType: Int = 0,
        val appid: Int = 0,
        val uin: Int = 0
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


