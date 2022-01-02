package kurenai.imsyncbot.handler.qq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.*
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.config.BotProperties
import kurenai.imsyncbot.config.GroupConfig.qqTg
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.entity.FileCache
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.service.TelegramId
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.HttpUtil
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import mu.KotlinLogging
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.contact.file.AbsoluteFileFolder.Companion.extension
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

@Component
class QQForwardHandler(
    final val properties: ForwardHandlerProperties,
    private val botProperties: BotProperties,
    private val cacheService: CacheService,
) : QQHandler {

    private val log = KotlinLogging.logger {}

    private val xmlMapper = XmlMapper()
    private val jsonMapper = ObjectMapper()
    private val picToFileSize = properties.picToFileSize * 1024 * 1024
    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"
    private var enableRecall = properties.enableRecall
    private var groupForwardContext = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(20),
        object : ThreadFactory {
            private val counter = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "QQForwardMsg#${counter.getAndIncrement()}").also {
                    it.isDaemon = true
                }
            }
        }).asCoroutineDispatcher()

    init {
        if (properties.tgMsgFormat.contains("\$msg")) tgMsgFormat = properties.tgMsgFormat
        if (properties.qqMsgFormat.contains("\$msg")) qqMsgFormat = properties.qqMsgFormat
    }

    @Throws(Exception::class)
    override suspend fun onGroupMessage(event: GroupAwareMessageEvent): Int {
        val group = event.group
        val sender = event.sender
        val senderName = BotUtil.formatUsername(
            UserConfig.idBindings[sender.id] ?: ContextHolder.qqBot.getFriend(sender.id)?.remarkOrNick
            ?: (sender as Member).remarkOrNameCardOrNick
        )
        val messageChain = event.message
        val atAccount = AtomicLong(-100)
        val content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(group, atAccount, it) }
        val chatId = qqTg[group.id] ?: ContextHolder.defaultTgGroup

        return if (content.startsWith("<?xml version='1.0'")) {
            handleRichMessage(event, chatId, senderName)
        } else if (content.contains("\"app\":")) {
            handleAppMessage(event, chatId, senderName)
        } else if (messageChain.contains(ForwardMessage.Key)) {
            onGroupForwardMessage(
                messageChain[ForwardMessage.Key]!!,
                group,
                chatId.toString(),
                senderName
            )
        } else {
            handleGroupMessage(messageChain, event.group, chatId.toString(), sender.id, senderName)
        }
    }

    @Throws(TelegramApiException::class)
    override suspend fun onRecall(event: MessageRecallEvent.GroupRecall): Int {
        val message = cacheService.getTgByQQ(event.group.id, event.messageIds[0])
        message?.let {
            if (enableRecall) {
                ContextHolder.telegramBotClient.send(
                    DeleteMessage.builder().chatId(it.chatId.toString())
                        .messageId(it.messageId)
                        .build()
                )
            } else {
                ContextHolder.telegramBotClient.send(if (it.text.isNullOrBlank()) {
                    EditMessageCaption().apply {
                        chatId = it.chatId.toString()
                        messageId = it.messageId
                        caption = "~${it.caption.format2Markdown()}~\n"
                        parseMode = ParseMode.MARKDOWNV2
                    }
                } else {
                    EditMessageText("~${it.text.format2Markdown()}~\n").apply {
                        chatId = it.chatId.toString()
                        messageId = it.messageId
                        parseMode = ParseMode.MARKDOWNV2
                    }
                })
            }
        }
        return CONTINUE
    }

    private suspend fun handleAppMessage(event: GroupAwareMessageEvent, chatId: Long, senderName: String): Int {
        val jsonNode = jsonMapper.readTree(event.message.contentToString())
        val url = if (jsonNode["view"].asText("") == "news") {
            val news = jsonNode["meta"]?.get("news")
            news?.get("jumpUrl")?.asText() ?: ""
        } else {
            val item = jsonNode["meta"]?.get("detail_1")
            item?.get("qqdocurl")?.asText() ?: ""
        }
        if (url.isNotBlank()) return handleGroupMessage(buildMessageChain { add(handleUrl(url)) }, event.group, chatId.toString(), event.sender.id, senderName)
        return CONTINUE
    }

    private suspend fun handleRichMessage(event: GroupAwareMessageEvent, chatId: Long, senderName: String): Int {
        val jsonNode = xmlMapper.readTree(event.message.contentToString())
        val action = jsonNode["action"].asText("")
        val url: String
        if (action == "web") {
            url = handleUrl(jsonNode["url"]?.asText() ?: "")
            return handleGroupMessage(buildMessageChain { add(url) }, event.group, chatId.toString(), event.sender.id, senderName)
        }
        return CONTINUE
    }

    private fun handleUrl(url: String): String {
        if (StringUtils.isNotBlank(url) && url.contains("?") && url.contains("b23.tv")) {
            return url.substring(0, url.indexOf("?"))
        }
        return url
    }

    private suspend fun handleGroupMessage(
        messageChain: MessageChain,
        group: Group,
        chatId: String,
        senderId: Long,
        senderName: String,
    ): Int {
//        log.info { "${group.name}(${group.id}) - $senderName($senderId): ${messageChain.contentToString()}" }
        val rejectPic = botProperties.ban.picGroup.contains(group.id)
        if (rejectPic) log.debug { "Reject picture" }

        val client = ContextHolder.telegramBotClient
        val source = messageChain[OnlineMessageSource.Key]
        val replyId = messageChain[QuoteReply.Key]?.source?.ids?.get(0)?.let { cacheService.getTelegramIdByQQ(it) }
        val atAccount = AtomicLong(-100)
        var content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(group, atAccount, it, replyId != null) }

        val isMaster = group.bot.id == senderId || ContextHolder.masterOfQQ.contains(senderId)

        if (isMaster && changeMsgFormatCmd(content)) {
            val demoContent = "demo msg."
            val demoMsg = qqMsgFormat
                .replace(BotUtil.NEWLINE_PATTERN, "\n")
                .replace(BotUtil.NAME_PATTERN, "demo username")
                .replace(BotUtil.ID_PATTERN, "123456789")
                .replace(BotUtil.MSG_PATTERN, demoContent)
            group.sendMessage("changed msg format\ne.g.\n$demoMsg")
            return END
        }

        if (content.startsWith("\n")) {
            content = content.substring(1)
        }

        val msg = tgMsgFormat.replace(BotUtil.NEWLINE_PATTERN, "\n", true)
            .replace(BotUtil.ID_PATTERN, senderId.toString(), true)
            .replace(BotUtil.NAME_PATTERN, senderName.replace(" @", " "), true)
            .replace(BotUtil.MSG_PATTERN, content, true)

        if (chatId.isBlank() || chatId == "0") return CONTINUE
        val count = messageChain.filterIsInstance<Image>().count()
        if (count > 1) {
            val medias = messageChain.filterIsInstance<Image>()
                .map { image ->
                    cacheService.getFile(image.imageId)?.let {
                        return@map InputMediaPhoto(it.fileId).apply { mediaName = image.imageId }
                    }

                    val url = image.queryUrl()
                    InputMediaPhoto.builder().media(url).mediaName(image.imageId).build()
                }.let { ArrayList(it) }
            if (medias.isNotEmpty()) {
                medias[0].caption = msg

                if (rejectPic) {
                    sendSimpleMedia(chatId, replyId, medias.map { it.media }, msg, source)
                } else {
                    val gifMedias = ArrayList<InputMediaPhoto>()
                    for (i in 0 until medias.size) {
                        if (medias[i].mediaName.endsWith(".git")) {
                            gifMedias.add(medias.removeAt(i))
                        }
                    }
                    try {
                        sendGroupMedias(chatId, replyId, medias, source)
                        sendGroupMedias(chatId, replyId, gifMedias, source)
                    } catch (e: Exception) {
                        log.error(e) { e.message }
                        sendSimpleMedia(chatId, replyId, medias.map { it.media }, msg, source)
                    }
                }
            }
        } else if (count > 0) {
            messageChain.filterIsInstance<Image>().forEach { image ->
                val imageSize: Long
                val aspectRatio = image.width.toFloat() / image.height.toFloat()
                var sendByFile = aspectRatio > 10 || aspectRatio < 0.1 || image.width > 1920 || image.height > 1920 || image.size > picToFileSize
                val inputFile = cacheService.getFile(image.imageId).let {
                    if (it == null) {

                        val file = File(BotUtil.getImagePath(image.imageId))
                        if (!rejectPic) {
                            if (!file.exists() || !file.isFile) {
                                val url: String = image.queryUrl()
                                HttpUtil.download(url, file)
                            }
                        }
                        imageSize = file.length()
                        InputFile(file)
                    } else {
                        imageSize = it.fileSize
                        InputFile(it.fileId)
                    }
                }
                if (imageSize > picToFileSize) sendByFile = true

                try {
                    if (rejectPic) {
                        sendSimpleMedia(chatId, replyId, listOf(image.queryUrl()), msg, source)
                    } else if (image.imageId.endsWith(".gif")) {
                        val builder = SendAnimation.builder()
                        replyId?.messageId?.let(builder::replyToMessageId)
                        client.send(
                            builder
                                .chatId(chatId)
                                .caption(msg)
                                .animation(inputFile)
                                .build()
                        )
                    } else {
                        if (sendByFile) {
                            val builder = SendDocument.builder()
                            replyId?.messageId?.let(builder::replyToMessageId)
                            client.send(
                                builder.caption(msg).chatId(chatId).document(inputFile).thumb(inputFile).build()
                            )
                        } else {
                            val builder = SendPhoto.builder()
                            replyId?.messageId?.let(builder::replyToMessageId)
                            client.send(builder.caption(msg).chatId(chatId).photo(inputFile).build())
                        }
                    }
                } catch (e: Exception) {
                    log.error(e) { "Send image fail." }
                    try {
                        client.send(SendDocument(chatId, inputFile).apply {
                            caption = msg
                        })
                    } catch (e: Exception) {
                        log.error(e) { "Send image fall back to send document fail." }
                        sendSimpleMedia(chatId, replyId, listOf(image.queryUrl()), msg, source)
                    }
                }?.let { m ->
                    cacheMsg(source, m, inputFile, image.imageId, imageSize)
                }
            }
        } else if (messageChain.contains(FileMessage.Key)) {
            CoroutineScope(Dispatchers.IO).launch {
                val absoluteFile = messageChain[FileMessage.Key]!!.toAbsoluteFile(group) ?: return@launch
                val url: String = absoluteFile.getUrl() ?: return@launch
                try {
                    val file = File(BotUtil.getDocumentPath(absoluteFile.name))
                    if (!file.exists() || !file.isFile) withContext(Dispatchers.IO) {
                        HttpUtil.download(url, file)
                    }
                    val inputFile = InputFile(file)
                    val extension: String = absoluteFile.extension.lowercase()
                    if (listOf("mp4", "mkv").contains(extension)) {
                        client.send(SendVideo.builder().video(inputFile).chatId(chatId).caption(msg).build())
                    } else if (extension == "gif") {
                        client.send(SendAnimation.builder().animation(inputFile).chatId(chatId).caption(msg).build())
                    } else if (listOf("bmp", "jpeg", "jpg", "png").contains(extension)) {
                        client.send(
                            SendDocument.builder().document(inputFile).thumb(InputFile(url)).chatId(chatId).caption(msg).build()
                        )
                    } else {
                        client.send(SendDocument.builder().document(inputFile).chatId(chatId).caption(msg).build())
                    }
                } catch (e: Exception) {
                    log.error(e) { e.message }
                    sendSimpleMedia(chatId, replyId, listOf(url), msg, source, absoluteFile.name)
                }
            }
        } else if (messageChain.contains(OnlineAudio.Key)) {
            val voice = messageChain[OnlineAudio.Key]
            voice?.urlForDownload?.let { url ->
                val file = File(url)
                if (!file.exists() || !file.isFile) {
                    HttpUtil.download(url, file)
                }
                try {
                    client.send(SendVoice.builder().chatId(chatId).voice(InputFile(file)).build())
                } catch (e: Exception) {
                    sendSimpleMedia(chatId, replyId, listOf(url), msg, source, "语音")
                }
            }
        } else {
            val builder = SendMessage.builder()
            replyId?.messageId?.let(builder::replyToMessageId)
            client.send(builder.chatId(chatId).text(msg).build())
                .let { m ->
                    source?.let { cacheService.cache(source, m) }
                }
        }
        return CONTINUE
    }

    @Throws(java.lang.Exception::class)
    private suspend fun onGroupForwardMessage(
        msg: ForwardMessage,
        group: Group,
        chatId: String,
        senderName: String,
    ): Int {
        if (msg.nodeList.map { it.messageChain }.count { it.contains(Image) } > 20) return END
        return withContext(groupForwardContext) {
            for ((senderId, _, forwardSenderName, messageChain) in msg.nodeList) {
                try {
                    handleGroupMessage(
                        messageChain,
                        group,
                        chatId,
                        senderId,
                        "$senderName forward from $forwardSenderName"
                    )
                } catch (e: java.lang.Exception) {
                    log.error(e) { e.message }
                }
            }
            CONTINUE
        }
    }

    suspend fun onGroupEvent(event: GroupEvent) {
        val msg = when (event) {
            is MemberJoinEvent -> {
                val tag = "\\#入群 \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberJoinEvent.Active -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`入群`${event.group.name}`"
                    }
                    is MemberJoinEvent.Invite -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`通过`${(UserConfig.idBindings[event.invitor.id] ?: event.invitor.remarkOrNameCardOrNick).format2Markdown()}` \\#id${event.invitor.id}\\ 的邀请入群`${event.group.name}`"
                    }
                    else -> return
                }
            }
            is MemberLeaveEvent -> {
                val tag = "\\#退群 \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberLeaveEvent.Kick -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`被踢出群`${event.group.name}`"
                    }
                    is MemberLeaveEvent.Quit -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`退出群`${event.group.name}`"
                    }
                    else -> return
                }
            }
            is MemberMuteEvent -> {
                "\\#禁言\n\\#id${event.member.id}\n`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`被禁言${event.durationSeconds / 60}分钟"
            }
            is GroupMuteAllEvent -> {
                "\\#禁言\n`${(UserConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.format2Markdown() ?: "?"}` \\#id${event.operator?.id ?: "?"} 禁言了所有人"
            }
            is MemberUnmuteEvent -> {
                "\\#禁言\n`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}` \\#id${event.member.id} 被`${(UserConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.format2Markdown() ?: "?"})` \\#id${event.operator?.id ?: "?"} 解除禁言"
            }
            is MemberCardChangeEvent -> {
                if (event.new.isNotEmpty()) {
                    "\\#名称 \\#id${event.member.id}\n`${(UserConfig.idBindings[event.member.id] ?: event.origin).format2Markdown()}`名称改为`${event.new.format2Markdown()}`"
                } else {
                    return
                }
            }
            is MemberSpecialTitleChangeEvent -> {
                "\\#头衔 \\#id${event.member.id}\n`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()})`获得头衔`${event.new.format2Markdown()}`"
            }
            else -> {
                log.debug { "未支持群事件 ${event.javaClass} 的处理" }
                return
            }
        }
        val chatId = qqTg[event.group.id] ?: ContextHolder.defaultTgGroup
        ContextHolder.telegramBotClient.send(SendMessage(chatId.toString(), msg).apply { parseMode = ParseMode.MARKDOWNV2 })
    }

    //TODO: 优化
    private fun getSingleContent(group: Group, atAccount: AtomicLong, msg: SingleMessage, hasReply: Boolean = true): String {
        return if (msg is At) {
            val target = msg.target
            if (hasReply && target == group.bot.id) return ""
            if (target == atAccount.get()) return "" else atAccount.set(target)
            val name = if (target == group.bot.id && !hasReply) {
                ContextHolder.masterUsername
            } else {
                UserConfig.idBindings[target]
                    ?: ContextHolder.qqBot.getFriend(target)?.remarkOrNick
                    ?: group.getMember(target)?.remarkOrNameCardOrNick?.let { BotUtil.formatUsername(it) }
                    ?: target.toString()
            }
            if (!name.startsWith("@")) {
                " @$name "
            } else {
                " $name "
            }
        } else {
            msg.contentToString()
        }
    }

    private suspend fun sendGroupMedias(chatId: String, replyId: TelegramId?, medias: List<InputMediaPhoto>, source: OnlineMessageSource?) {
        if (medias.isEmpty()) return
        val mediaGroups = ArrayList<List<InputMediaPhoto>>()
        var offset = 0
        val increase = 5
        while (offset < medias.size) {
            val value = ArrayList<InputMediaPhoto>()
            for (n in offset until min(offset + increase, medias.size)) {
                value.add(medias[n])
            }
            mediaGroups.add(value)
            offset += increase
        }

        var count = 0
        mediaGroups.forEach { list ->
            val client = ContextHolder.telegramBotClient
            try {
                if (list.size > 1) {
                    val builder = SendMediaGroup.builder()
                    replyId?.messageId?.let(builder::replyToMessageId)
                    client.send(
                        builder
                            .medias(list)
                            .chatId(chatId)
                            .build()
                    ).let { result ->
                        source?.let { source -> cacheService.cache(source, result[0]) }
                    }
                } else if (list.size == 1) {
                    val builder = SendPhoto.builder()
                    replyId?.messageId?.let(builder::replyToMessageId)
                    val file = list[0].newMediaFile
                    client.send(
                        builder
                            .photo(InputFile(file, file.name))
                            .chatId(chatId)
                            .caption(list[0].caption)
                            .build()
                    ).let { result ->
                        source?.let { source -> cacheService.cache(source, result) }
                    }
                }
            } catch (e: Exception) {
                log.error(e) { "Send group medias[$count] fail." }
                sendSimpleMedia(chatId, replyId, medias.map(InputMediaPhoto::getMedia), medias[0].caption, source)
            }
            count++
        }
    }

    private suspend fun sendSimpleMedia(chatId: String, replyId: TelegramId?, urls: List<String>, msg: String?, source: OnlineMessageSource?, mask: String = "图片"): Message {
        var urlStr = ""
        for (url in urls) {
            urlStr += "[${mask.format2Markdown()}](${url.format2Markdown()})\n"
        }
        return ContextHolder.telegramBotClient.send(SendMessage(chatId, "${msg?.format2Markdown()}$urlStr").apply {
            this.replyToMessageId = replyId?.messageId
            this.parseMode = ParseMode.MARKDOWNV2
        }).let { rec ->
            source?.let { source -> cacheService.cache(source, rec) }
            rec
        }
    }

    private fun cacheMsg(source: OnlineMessageSource?, recMsg: Message, inputFile: InputFile? = null, qqFileId: String = "", imageSize: Long = 0L) {
        if (inputFile != null && inputFile.isNew) {
            when {
                recMsg.hasDocument() -> {
                    recMsg.document.fileId
                }
                recMsg.hasAnimation() -> {
                    recMsg.animation.fileId
                }
                recMsg.hasPhoto() -> {
                    recMsg.photo[0].fileId
                }
                else -> null
            }?.let { fileId ->
                cacheService.cacheFile(qqFileId, FileCache(fileId, imageSize))
            }
        }
        source?.let {
            cacheService.cache(source, recMsg)
        }
    }

    private fun changeMsgFormatCmd(text: String): Boolean {
        return if (text.startsWith("/msg") && text.length > 4) {
            text.substring(5).takeIf { it.isNotBlank() }?.let {
                qqMsgFormat = it
                log.info { "Change qq message format: $qqMsgFormat" }
                true
            } ?: false
        } else false
    }

    override fun order(): Int {
        return 150
    }

}