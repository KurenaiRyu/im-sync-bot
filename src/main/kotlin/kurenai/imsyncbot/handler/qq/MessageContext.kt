package kurenai.imsyncbot.handler.qq

import com.fasterxml.jackson.core.exc.StreamReadException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.callback.impl.GetFileUrlCallback.Companion.METHOD
import kurenai.imsyncbot.domain.QQAppMessage
import kurenai.imsyncbot.domain.QQRichMessage
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.BotUtil.formatUsername
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardButton
import moe.kurenai.tdlight.model.keyboard.InlineKeyboardMarkup
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.request.message.*
import moe.kurenai.tdlight.util.MarkdownUtil.fm2md
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.MiraiExperimentalApi
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * 信息上下文
 * @author Kurenai
 * @since 9/2/2022 15:12:44
 */
sealed interface MessageContext

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

    suspend fun getType() = type ?: handleType(messageChain).also { type = it }

    @OptIn(MiraiExperimentalApi::class)
    private suspend fun handleType(messageChain: MessageChain): MessageType {
        return if (messageChain.contains(RichMessage.Key)) {
            val content = messageChain[RichMessage.Key]!!.content
            try {
                val document = Jsoup.parse(content, Parser.xmlParser())
                val uuid = document.selectXpath("//image").attr("uuid")
                if (uuid.isNotBlank()) {
                    SingleImage(Image(uuid), true)
                } else {
                    Rich(xmlMapper.readValue(content, QQRichMessage::class.java))
                }
            } catch (e: Exception) {
                try {
                    App(jsonMapper.readValue(content, QQAppMessage::class.java))
                } catch (e: StreamReadException) {
                    Normal()
                }
            }
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

    private fun String.handleUrl(): String {
        return if (this.contains("b23.tv")) {
            this.replace("b23.tv", "b23.wtf")
        } else this
    }

    companion object {
        private val jsonMapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        private val xmlMapper = XmlMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        private const val maxImageRatio = 16 / 7.0F
        private const val minImageRatio = 7 / 16.0F
    }

    sealed interface MessageType
    inner class Rich(val msg: QQRichMessage) : MessageType {
        val url: String? = msg.url?.handleUrl()
        val telegramMessage: SendMessage = SendMessage(
            chatId,
            (url?.fm2md()?.formatMsg(senderId, senderName) ?: simpleContent)
        ).apply {
            parseMode = ParseMode.MARKDOWN_V2
            replyId?.let { replyToMessageId = replyId }
        }
    }

    inner class App(val msg: QQAppMessage) : MessageType {
        val url: String? = (msg.meta.news?.jumpUrl ?: msg.meta.detail1?.qqdocurl)?.handleUrl()
        val telegramMessage: SendMessage = SendMessage(
            chatId,
            (url?.fm2md()?.formatMsg(senderId, senderName) ?: simpleContent)
        ).apply {
            parseMode = ParseMode.MARKDOWN_V2
            replyId?.let { replyToMessageId = replyId }
        }
    }

    inner class SingleImage(val image: Image, val onlyImage: Boolean = false) : MessageType {

        val ratio: Float = image.width / image.height.toFloat()
        val shouldBeFile: Boolean = !image.isEmoji &&
                (ratio > maxImageRatio || ratio < minImageRatio)

        suspend fun getImageMessage(): SendPhoto {
            return SendPhoto(chatId, InputFile(image.queryUrl())).apply {
                parseMode = ParseMode.MARKDOWN_V2
                caption = if (onlyImage) {
                    "".formatMsg(senderId, senderName)
                } else {
                    getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                }
                replyId?.let { replyToMessageId = replyId }
            }
        }

        suspend fun getFileMessage(): SendDocument {
            return SendDocument(chatId, InputFile(image.queryUrl())).apply {
                parseMode = ParseMode.MARKDOWN_V2
                caption = if (onlyImage) {
                    "".formatMsg(senderId, senderName)
                } else {
                    getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                }
                replyId?.let { replyToMessageId = replyId }
            }
        }
    }

    inner class MultiImage(val images: List<Image>) : MessageType {
        val shouldBeFile: Boolean = images.any {
            if (it.imageType == ImageType.GIF || it.imageType == ImageType.APNG || it.imageType == ImageType.UNKNOWN && !it.isEmoji)
                return@any true
            val ratio = it.width / it.height.toFloat()
            ratio > maxImageRatio || ratio < minImageRatio
        }

        suspend fun getTelegramMessage(): SendMediaGroup {
            return SendMediaGroup(chatId).apply {
                media = images.mapNotNull {
                    if (it.imageType == ImageType.GIF) null
                    else if (shouldBeFile)
                        InputMediaDocument(InputFile(it.queryUrl())).apply {
                            parseMode = ParseMode.MARKDOWN_V2
                        }
                    else
                        InputMediaPhoto(InputFile(it.queryUrl())).apply {
                            parseMode = ParseMode.MARKDOWN_V2
                        }
                }.also {
                    it[0].apply {
                        caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                    }
                }
                replyId?.let { replyToMessageId = replyId }
            }
        }
    }

    inner class GifImage(
        val image: Image,
    ) : MessageType {
        suspend fun getTelegramMessage(): SendAnimation {
            return SendAnimation(chatId, InputFile(image.queryUrl())).apply {
                caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                parseMode = ParseMode.MARKDOWN_V2
                replyId?.let { replyToMessageId = replyId }
            }
        }
    }

    inner class Video(val file: FileMessage) : MessageType {
        suspend fun getTelegramMessage(): SendVideo {
            val url = file.toAbsoluteFile(group)?.getUrl()
            require(url != null) { "获取视频地址失败" }
            return SendVideo(chatId, InputFile(url)).apply {
                caption = getContentWithAtAndWithoutImage().formatMsg(senderId, senderName)
                parseMode = ParseMode.MARKDOWN_V2
                replyId?.let { replyToMessageId = replyId }
            }
        }
    }

    inner class File(
        val file: FileMessage,
    ) : MessageType {
        val shouldBeFile = file.size < 50 * 1024 * 1024

        suspend fun getFileMessage(): SendDocument {
            return SendDocument(chatId, InputFile(getUrl())).apply {
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
                    callbackData = "$METHOD ${group.id} ${file.toAbsoluteFile(group)?.absolutePath ?: ""}"
                })))
            }
        }

        private suspend fun getUrl(): String {
            val url = file.toAbsoluteFile(group)?.getUrl()
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


