package kurenai.imsyncbot.handler.qq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.domain.FileCache
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.HttpUtil
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import mu.KotlinLogging
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import org.apache.commons.io.FileUtils
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

@Component
class QQForwardHandler(properties: ForwardHandlerProperties, private val cacheService: CacheService) : QQHandler {

    private val log = KotlinLogging.logger {}

    private val bindingName: Map<Long, String>
    private val picToFileSize = 500 * 1024
    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"

    init {
        bindingName = properties.member.bindingName
        if (properties.tgMsgFormat.contains("\$msg")) tgMsgFormat = properties.tgMsgFormat
        if (properties.qqMsgFormat.contains("\$msg")) qqMsgFormat = properties.qqMsgFormat
    }

    @Throws(Exception::class)
    override suspend fun onGroupMessage(event: GroupAwareMessageEvent): Int {
        val group = event.group
        val sender = event.sender
        val senderName = BotUtil.formatUsername(
            bindingName[sender.id] ?: ContextHolder.qqBot.getFriend(sender.id)?.remarkOrNick
            ?: (sender as Member).remarkOrNameCardOrNick
        )
        val messageChain = event.message
        val atAccount = AtomicLong(-100)
        val content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(group, atAccount, it) }
        val chatId = ContextHolder.qqTgBinding[group.id] ?: ContextHolder.defaultTgGroup

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return CONTINUE

        if (messageChain.contains(ForwardMessage.Key)) {
            return onGroupForwardMessage(
                messageChain[ForwardMessage.Key]!!,
                group,
                chatId.toString(),
                senderName
            )
        }

        return handleGroupMessage(messageChain, event.group, chatId.toString(), sender.id, senderName)
    }

    @Throws(TelegramApiException::class)
    override suspend fun onRecall(event: MessageRecallEvent): Int {
        val message = cacheService.getByQQ(event.messageIds[0])
        message?.let {
            ContextHolder.telegramBotClient.execute(
                DeleteMessage.builder().chatId(it.chatId.toString())
                    .messageId(it.messageId)
                    .build()
            )
        }
        return CONTINUE
    }

    private suspend fun handleGroupMessage(
        messageChain: MessageChain,
        group: Group,
        chatId: String,
        senderId: Long,
        senderName: String,
    ): Int {
        log.debug { "${group.name}(${group.id}) - $senderName($senderId): ${messageChain.contentToString()}" }

        val client = ContextHolder.telegramBotClient
        val source = messageChain[OnlineMessageSource.Key]
        val replyId = messageChain[QuoteReply.Key]?.source?.ids?.get(0)?.let { cacheService.getIdByQQ(it) }
        val atAccount = AtomicLong(-100)
        var content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(group, atAccount, it) }

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return CONTINUE

        val isMaster = ContextHolder.qqBot.id == senderId || ContextHolder.masterOfQQ == senderId

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
        } else if (count > 0) {
            messageChain.filterIsInstance<Image>().forEach { image ->
                val imageSize: Long
                val inputFile = cacheService.getFile(image.imageId).let {
                    if (it == null) {

                        val file = File(BotUtil.getImagePath(image.imageId))
                        if (!file.exists() || !file.isFile) {
                            val url: String = image.queryUrl()
                            withContext(Dispatchers.IO) {
                                FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                            }
                        }
                        imageSize = file.length()
                        InputFile(file)
                    } else {
                        imageSize = it.fileSize
                        InputFile(it.fileId)
                    }
                }

                try {
                    if (image.imageId.endsWith(".gif")) {
                        val builder = SendAnimation.builder()
                        replyId?.let(builder::replyToMessageId)
                        client.execute(
                            builder
                                .chatId(chatId)
                                .caption(msg)
                                .animation(inputFile)
                                .build()
                        )
                    } else {
                        if (imageSize > picToFileSize) {
                            val builder = SendDocument.builder()
                            replyId?.let(builder::replyToMessageId)
                            client.send(
                                builder.caption(msg).chatId(chatId).document(inputFile).thumb(inputFile).build()
                            )
                        } else {
                            val builder = SendPhoto.builder()
                            replyId?.let(builder::replyToMessageId)
                            client.send(builder.caption(msg).chatId(chatId).photo(inputFile).build())
                        }
                    }
                } catch (e: Exception) {
                    log.error(e) { "Send image fail." }
                    try {
                        client.send(SendDocument(chatId, inputFile))
                    } catch (e: Exception) {
                        log.error(e) { "Send image fall back to send document fail." }
                        sendSimpleMedia(chatId, replyId, listOf(image.queryUrl()), msg, source)
                    }
                }?.let { m ->
                    cacheMsg(source, m, inputFile, image.imageId, imageSize)
                }
            }
        } else if (messageChain.contains(FileMessage.Key)) {
            val downloadInfo = messageChain[FileMessage.Key]!!.toRemoteFile(group)?.getDownloadInfo() ?: return CONTINUE
            val url: String = downloadInfo.url
            try {
                val file = File(BotUtil.getDocumentPath(downloadInfo.filename))
                if (!file.exists() || !file.isFile) withContext(Dispatchers.IO) {
                    FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                }
                val inputFile = InputFile(file)
                val filename: String = downloadInfo.filename.lowercase()
                if (filename.endsWith(".mkv") || filename.endsWith(".mp4")) {
                    client.send(SendVideo.builder().video(inputFile).chatId(chatId).caption(msg).build())
                } else if (filename.endsWith(".bmp") || filename.endsWith(".jpeg") || filename.endsWith(".jpg") || filename.endsWith(".png")) {
                    client.send(
                        SendDocument.builder().document(inputFile).thumb(InputFile(url)).chatId(chatId).caption(msg).build()
                    )
                } else {
                    client.send(SendDocument.builder().document(inputFile).chatId(chatId).caption(msg).build())
                }
            } catch (e: Exception) {
                log.error(e) { e.message }
                sendSimpleMedia(chatId, replyId, listOf(url), msg, source, downloadInfo.filename)
            }
        } else if (messageChain.contains(OnlineAudio.Key)) {
            val voice = messageChain[OnlineAudio.Key]
            voice?.urlForDownload?.let { url ->
                val file = File(url)
                if (!file.exists() || !file.isFile) {
                    withContext(Dispatchers.IO) {
                        FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                    }
                }
                try {
                    client.send(SendVoice.builder().chatId(chatId).voice(InputFile(file)).build())
                } catch (e: Exception) {
                    sendSimpleMedia(chatId, replyId, listOf(url), msg, source, "语音")
                }
            }
        } else {
            val builder = SendMessage.builder()
            replyId?.let(builder::replyToMessageId)
            client.execute(builder.chatId(chatId).text(msg).build())
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
        return CONTINUE
    }

    fun onGroupEvent(event: GroupEvent) {
        val msg = when (event) {
            is MemberJoinEvent -> {
                val tag = "\\#入群"
                when (event) {
                    is MemberJoinEvent.Active -> {
                        "$tag *${event.member.remarkOrNameCardOrNick.format2Markdown()}\\(${event.member.id}\\)*主动入群"
                    }
                    is MemberJoinEvent.Invite -> {
                        "$tag *${event.member.remarkOrNameCardOrNick.format2Markdown()}\\(${event.member.id}\\)*通过*${event.invitor.remarkOrNameCardOrNick.format2Markdown()}\\(${event.invitor.id}\\)*的邀请入群"
                    }
                    else -> return
                }
            }
            is MemberLeaveEvent -> {
                val tag = "\\#退群"
                when (event) {
                    is MemberLeaveEvent.Kick -> {
                        "$tag *${event.member.remarkOrNameCardOrNick.format2Markdown()}\\(${event.member.id}\\)*被踢出群"
                    }
                    is MemberLeaveEvent.Quit -> {
                        "$tag *${event.member.remarkOrNameCardOrNick.format2Markdown()}\\(${event.member.id}\\)*主动退群"
                    }
                    else -> return
                }
            }
            is MemberMuteEvent -> {
                "\\#禁言 *${event.member.remarkOrNameCardOrNick.format2Markdown()}\\(${event.member.id}\\)*被禁言${event.durationSeconds / 60}分钟"
            }
            is GroupMuteAllEvent -> {
                "\\#禁言 *${event.operator?.remarkOrNameCardOrNick?.format2Markdown()}(${event.operator?.id})*禁言了所有人"
            }
            is MemberUnmuteEvent -> {
                "\\#解除禁言 *${event.member.remarkOrNameCardOrNick.format2Markdown()}\\(${event.member.id}\\)*被*${event.operator?.remarkOrNameCardOrNick?.format2Markdown()}\\(${event.operator?.id}\\)*解除禁言"
            }
            is MemberCardChangeEvent -> {
                if (event.new.isNotEmpty()) {
                    "\\#名称 *${event.member.remarkOrNameCardOrNick.format2Markdown()}\\(${event.member.id}\\)*将名字*${event.origin.format2Markdown()}*改为*${event.new.format2Markdown()}*"
                } else {
                    return
                }
            }
            is MemberSpecialTitleChangeEvent -> {
                "\\#头衔 *${event.member.remarkOrNameCardOrNick.format2Markdown()}\\(${event.member.id}\\)*获得头衔*${event.new.format2Markdown()}*"
            }
            else -> return
        }
        val chatId = ContextHolder.qqTgBinding[event.group.id] ?: ContextHolder.defaultTgGroup
        ContextHolder.telegramBotClient.execute(SendMessage(chatId.toString(), msg).apply { parseMode = ParseMode.MARKDOWNV2 })
    }

    private fun getSingleContent(group: Group, atAccount: AtomicLong, msg: SingleMessage): String {
        return if (msg is At) {
            val target = msg.target
            if (target == atAccount.get()) return "" else atAccount.set(target)
            var name = bindingName[target]
                ?: ContextHolder.qqBot.getFriend(target)?.remarkOrNick
                ?: group.getMember(target)?.remarkOrNameCardOrNick?.let { BotUtil.formatUsername(it) }
                ?: target.toString()
            if (!name.startsWith("@")) {
                name = "@$name"
            }
            " $name "
        } else {
            msg.contentToString()
        }
    }

    private fun sendGroupMedias(chatId: String, replyId: Int?, medias: List<InputMediaPhoto>, source: OnlineMessageSource?) {
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
                    replyId?.let(builder::replyToMessageId)
                    client.execute(
                        builder
                            .medias(list)
                            .chatId(chatId)
                            .build()
                    ).let { result ->
                        source?.let { source -> cacheService.cache(source, result[0]) }
                    }
                } else if (list.size == 1) {
                    val builder = SendPhoto.builder()
                    replyId?.let(builder::replyToMessageId)
                    val file = list[0].newMediaFile
                    client.execute(
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

    private fun sendSimpleMedia(chatId: String, replyId: Int?, urls: List<String>, msg: String?, source: OnlineMessageSource?, mask: String = "图片"): Message {
        var urlStr = ""
        for (url in urls) {
            urlStr += "[$mask]($url)\n"
        }
        return ContextHolder.telegramBotClient.execute(SendMessage(chatId, "$urlStr${msg?.format2Markdown()}").apply {
            this.replyToMessageId = replyId
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