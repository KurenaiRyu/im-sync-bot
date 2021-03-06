package kurenai.imsyncbot.handler.qq

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.GroupConfig.qqTg
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.configProperties
import kurenai.imsyncbot.entity.FileCache
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.qq.QQBotClient.bot
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import moe.kurenai.tdlight.exception.TelegramApiException
import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.MessageEntity
import moe.kurenai.tdlight.request.message.*
import moe.kurenai.tdlight.util.MarkdownUtil.fm2md
import mu.KotlinLogging
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.contact.file.AbsoluteFileFolder.Companion.extension
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import org.apache.commons.lang3.StringUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class QQMessageHandler : QQHandler {

    private val log = KotlinLogging.logger {}

    private val xmlMapper = XmlMapper()
    private val jsonMapper = ObjectMapper()
    private val picToFileSize = configProperties.handler.picToFileSize * 1024 * 1024
    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"
    private var enableRecall = configProperties.handler.enableRecall
    private var groupForwardContext = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
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
        if (configProperties.handler.tgMsgFormat.contains("\$msg")) tgMsgFormat = configProperties.handler.tgMsgFormat
        if (configProperties.handler.qqMsgFormat.contains("\$msg")) qqMsgFormat = configProperties.handler.qqMsgFormat
    }

    @Throws(Exception::class)
    override suspend fun onGroupMessage(group: Group, messageChain: MessageChain): Int {
        val messageSource = messageChain.source
        val sender = group.getOrFail(messageSource.fromId)
        val senderName = BotUtil.formatUsername(
            UserConfig.idBindings[sender.id] ?: bot.getFriend(sender.id)?.remarkOrNick
            ?: (sender as Member).remarkOrNameCardOrNick
        )
        val content = messageChain.contentToString()
        val chatId = qqTg[group.id] ?: GroupConfig.defaultTgGroup

        return if (content.startsWith("<?xml version='1.0'")) {
            handleRichMessage(messageChain, group, sender, chatId, senderName)
        } else if (content.contains("\"app\":")) {
            handleAppMessage(messageChain, group, sender, chatId, senderName)
        } else if (messageChain.contains(ForwardMessage.Key)) {
            onGroupForwardMessage(
                messageChain[ForwardMessage.Key]!!,
                group,
                chatId.toString(),
                senderName
            )
        } else {
            handleGroupMessage(messageChain, group, chatId.toString(), sender.id, senderName)
        }
    }

    @Throws(TelegramApiException::class)
    override suspend fun onRecall(event: MessageRecallEvent.GroupRecall): Int {
        CacheService.getTgByQQ(event.group.id, event.messageIds[0])?.let { message ->
            if (enableRecall) {
                DeleteMessage(message.chatId, message.messageId!!).send()
            } else {
                if (message.text.isNullOrBlank()) {
                    EditMessageCaption().apply {
                        chatId = message.chatId
                        messageId = message.messageId
                        caption = "~${message.caption!!.format2Markdown()}~\n"
                        parseMode = ParseMode.MARKDOWN_V2
                    }.send()
                } else {
                    EditMessageText("~${message.text!!.format2Markdown()}~\n").apply {
                        chatId = message.chatId
                        messageId = message.messageId
                        parseMode = ParseMode.MARKDOWN_V2
                    }.send()
                }
            }
        }
        return CONTINUE
    }

    private suspend fun handleAppMessage(
        messageSource: MessageChain,
        group: Group,
        sender: User,
        chatId: Long,
        senderName: String
    ): Int {
        val jsonNode = jsonMapper.readTree(messageSource.contentToString())
        val url = if (jsonNode["view"].asText("") == "news") {
            val news = jsonNode["meta"]?.get("news")
            news?.get("jumpUrl")?.asText() ?: ""
        } else {
            val item = jsonNode["meta"]?.get("detail_1")
            item?.get("qqdocurl")?.asText() ?: ""
        }
        if (url.isNotBlank()) return handleGroupMessage(
            buildMessageChain { add(handleUrl(url)) },
            group,
            chatId.toString(),
            sender.id,
            senderName
        )
        return CONTINUE
    }

    private suspend fun handleRichMessage(
        messageChain: MessageChain,
        group: Group,
        sender: User,
        chatId: Long,
        senderName: String
    ): Int {
        val jsonNode = xmlMapper.readTree(messageChain.contentToString())
        val action = jsonNode["action"].asText("")
        val url: String
        if (action == "web") {
            url = handleUrl(jsonNode["url"]?.asText() ?: "")
            return handleGroupMessage(buildMessageChain { add(url) }, group, chatId.toString(), sender.id, senderName)
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
        val rejectPic = GroupConfig.picBannedGroups.contains(group.id)
        if (rejectPic) log.debug { "Reject picture" }

        val replyId =
            messageChain[QuoteReply.Key]?.let { CacheService.getTgIdByQQ(it.source.targetId, it.source.ids[0]) }

        val (content, atEntities) = messageChain.getContentAndAtEntities(group, replyId != null)

//        val isMaster = group.bot.id == senderId || UserConfig.masterQQ == senderId

//        if (isMaster && changeMsgFormatCmd(text)) {
//            val demoContent = "demo msg."
//            val demoMsg = qqMsgFormat
//                .replace(BotUtil.NEWLINE_PATTERN, "\n")
//                .replace(BotUtil.NAME_PATTERN, "demo_username")
//                .replace(BotUtil.ID_PATTERN, "123456789")
//                .replace(BotUtil.MSG_PATTERN, demoContent)
//            group.sendMessage("changed msg format\ne.g.\n$demoMsg")
//            return END
//        }

        var msg = tgMsgFormat.replace(BotUtil.NEWLINE_PATTERN, "\n", true)
            .replace(BotUtil.ID_PATTERN, senderId.toString(), true)
            .replace(BotUtil.NAME_PATTERN, senderName.replace(" @", " "), true)

        val plusOffset = msg.length - BotUtil.MSG_PATTERN.length
        atEntities.forEach { it.offset += plusOffset }

        msg = msg.replace(BotUtil.MSG_PATTERN, content, true)


        if (chatId.isBlank() || chatId == "0") return CONTINUE
        val count = messageChain.filterIsInstance<Image>().count()
        if (count > 1) {
            val mediaUrls = ArrayList<String>()
            val medias = messageChain.filterIsInstance<Image>()
                .map { image ->
                    CacheService.getFile(image.imageId)?.let {
                        return@map InputMediaPhoto(InputFile(it.fileId).apply { fileName = image.imageId })
                    }

                    val url = image.queryUrl()
                    mediaUrls.add(url)
                    InputMediaPhoto(InputFile(BotUtil.downloadImg(image.imageId, url)))
                }.let {
                    ArrayList(it)
                }
            if (medias.isNotEmpty()) {
                medias[0].caption = msg

                if (rejectPic) {
                    sendSimpleMedia(chatId, replyId, mediaUrls, msg, messageChain, atEntities)
                } else {
                    val gifMedias = ArrayList<InputMediaPhoto>()
                    for (i in 0 until medias.size) {
                        if (medias[i].media.fileName?.endsWith(".git") == true) {
                            gifMedias.add(medias.removeAt(i))
                        }
                    }
                    try {
                        if (medias.isNotEmpty()) sendGroupMedias(chatId, replyId, medias, messageChain, atEntities)
                        if (gifMedias.isNotEmpty()) sendGroupMedias(
                            chatId,
                            replyId,
                            gifMedias,
                            messageChain,
                            atEntities
                        )
                    } catch (e: Exception) {
                        log.error(e) { "Send image fall back to send simple media." }
                        sendSimpleMedia(chatId, replyId, mediaUrls, msg, messageChain, atEntities)
                    }
                }
            }
        } else if (count > 0) {
            messageChain.filterIsInstance<Image>().forEach { image ->
                val imageSize: Long
                val aspectRatio = image.width.toFloat() / image.height.toFloat()
                var sendByFile =
                    aspectRatio > 10 || aspectRatio < 0.1 || image.width > 1920 || image.height > 1920 || image.size > picToFileSize
                val inputFile = CacheService.getFile(image.imageId).let {
                    if (it == null) {

                        val url: String = image.queryUrl()
                        val file = BotUtil.downloadImg(image.imageId, url, rejectPic)
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
                        sendSimpleMedia(chatId, replyId, listOf(image.queryUrl()), msg, messageChain, atEntities)
                    } else if (image.imageId.endsWith(".gif")) {
                        SendAnimation(chatId, inputFile).apply {
                            replyToMessageId = replyId?.second
                            caption = msg
                            thumb = inputFile
                        }.send()
                    } else {
                        if (sendByFile) {
                            SendDocument(chatId, inputFile).apply {
                                replyToMessageId = replyId?.second
                                caption = msg
                                thumb = inputFile
                                if (atEntities.isNotEmpty()) captionEntities = atEntities
                            }.send()
                        } else {
                            SendPhoto(chatId, inputFile).apply {
                                replyToMessageId = replyId?.second
                                caption = msg
                                if (atEntities.isNotEmpty()) captionEntities = atEntities
                            }.send()
                        }
                    }
                } catch (e: Exception) {
                    log.error(e) { "Send image fail." }
                    try {
                        SendDocument(chatId, inputFile).apply {
                            caption = msg
                            if (atEntities.isNotEmpty()) captionEntities = atEntities
                        }.send()
                    } catch (e: Exception) {
                        log.error(e) { "Send image fall back to send simple media." }
                        sendSimpleMedia(chatId, replyId, listOf(image.queryUrl()), msg, messageChain, atEntities)
                    }
                }.also { m ->
                    cacheMsg(messageChain, m, inputFile, image.imageId, imageSize)
                }
            }
        } else if (messageChain.contains(FileMessage.Key)) {
            withContext(Dispatchers.IO) {
                val absoluteFile = messageChain[FileMessage.Key]!!.toAbsoluteFile(group) ?: return@withContext
                val url: String = absoluteFile.getUrl() ?: return@withContext
                try {
                    val file = withContext(Dispatchers.IO) {
                        BotUtil.downloadDoc(absoluteFile.name, url)
                    }
                    val inputFile = InputFile(file)
                    val extension: String = absoluteFile.extension.lowercase()
                    if (listOf("mp4", "mkv").contains(extension)) {
                        SendVideo(chatId, inputFile).apply {
                            caption = msg
                            if (atEntities.isNotEmpty()) captionEntities = atEntities
                        }.send()
                    } else if (extension == "gif") {
                        SendAnimation(chatId, inputFile).apply {
                            caption = msg
                            if (atEntities.isNotEmpty()) captionEntities = atEntities
                        }.send()
                    } else if (listOf("bmp", "jpeg", "jpg", "png").contains(extension)) {
                        SendDocument(chatId, inputFile).apply {
                            thumb = InputFile(url)
                            caption = msg
                            if (atEntities.isNotEmpty()) captionEntities = atEntities
                        }.send()
                    } else {
                        SendDocument(chatId, inputFile).apply {
                            this.caption = msg
                            if (atEntities.isNotEmpty()) captionEntities = atEntities
                        }
                    }
                } catch (e: Exception) {
                    log.error(e) { e.message }
                    sendSimpleMedia(chatId, replyId, listOf(url), msg, messageChain, atEntities, absoluteFile.name)
                }
            }
        } else if (messageChain.contains(OnlineAudio.Key)) {
            val voice = messageChain[OnlineAudio.Key]
            voice?.urlForDownload?.let { url ->
                val file = BotUtil.downloadDoc(voice.filename, url)
                try {
                    SendDocument(chatId, InputFile(file)).send()
                } catch (e: Exception) {
                    sendSimpleMedia(chatId, replyId, listOf(url), msg, messageChain, atEntities, "??????")
                }
            }
        } else {
            SendMessage(chatId, msg).apply {
                replyToMessageId = replyId?.second
                if (atEntities.isNotEmpty()) entities = atEntities
            }.send().also { m ->
                CacheService.cache(messageChain, m)
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
                val tag = "\\#?????? \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberJoinEvent.Active -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`??????`${event.group.name}`"
                    }
                    is MemberJoinEvent.Invite -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`??????`${(UserConfig.idBindings[event.invitor.id] ?: event.invitor.remarkOrNameCardOrNick).format2Markdown()}` \\#id${event.invitor.id}\\ ???????????????`${event.group.name}`"
                    }
                    else -> return
                }
            }
            is MemberLeaveEvent -> {
                val tag = "\\#?????? \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberLeaveEvent.Kick -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`????????????`${event.group.name}`"
                    }
                    is MemberLeaveEvent.Quit -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`?????????`${event.group.name}`"
                    }
                    else -> return
                }
            }
            is MemberMuteEvent -> {
                "\\#??????\n\\#id${event.member.id}\n`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`?????????${event.durationSeconds / 60}??????"
            }
            is GroupMuteAllEvent -> {
                "\\#??????\n`${(UserConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.format2Markdown() ?: "?"}` \\#id${event.operator?.id ?: "?"} ??????????????????"
            }
            is MemberUnmuteEvent -> {
                "\\#??????\n`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}` \\#id${event.member.id} ???`${(UserConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.format2Markdown() ?: "?"})` \\#id${event.operator?.id ?: "?"} ????????????"
            }
            is MemberCardChangeEvent -> {
                if (event.new.isNotEmpty()) {
                    "\\#?????? \\#id${event.member.id}\n`${(UserConfig.idBindings[event.member.id] ?: event.origin).format2Markdown()}`????????????`${event.new.format2Markdown()}`"
                } else {
                    return
                }
            }
            is MemberSpecialTitleChangeEvent -> {
                "\\#?????? \\#id${event.member.id}\n`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`????????????`${event.new.format2Markdown()}`"
            }
            else -> {
                log.debug { "?????????????????? ${event.javaClass} ?????????" }
                return
            }
        }
        val chatId = qqTg[event.group.id] ?: GroupConfig.defaultTgGroup
        SendMessage(chatId.toString(), msg).apply { parseMode = ParseMode.MARKDOWN_V2 }.send()
    }

    private fun MessageChain.getContentAndAtEntities(
        group: Group,
        hasReply: Boolean = true
    ): Pair<String, List<MessageEntity>> {
        var atAccount = -100L
        var text = ""
        val entities = ArrayList<MessageEntity>()
        for (msg in this) {
            if (msg is At) {
                val target = msg.target
                if (hasReply && target == group.bot.id) continue
                if (target == atAccount) continue
                else atAccount = target
                val id: Long?
                val name = if (target == group.bot.id && !hasReply) {
                    id = UserConfig.masterTg
                    UserConfig.masterUsername.takeIf { it.isNotBlank() } ?: "????????????"
                } else {
                    id = UserConfig.links.find { it.qq == target }?.tg
                    val tgBindName = UserConfig.qqUsernames[target] ?: UserConfig.idBindings[target]
                    if (tgBindName == null || tgBindName.isBlank()) {
                        val qqBindName = bot.getFriend(target)?.remarkOrNick?.takeIf { it.isNotBlank() }
                            ?: group.getMember(target)?.remarkOrNameCardOrNick?.takeIf { it.isNotBlank() }
                            ?: target.toString()
                        " @$qqBindName "
                    } else {
                        tgBindName
                    }
                }.let(BotUtil::formatUsername)
                if (id != null) {
                    entities.add(MessageEntity(
                        MessageEntityType.TEXT_MENTION,
                        text.length,
                        name.length
                    ).apply { user = moe.kurenai.tdlight.model.message.User(id) })
                    text += name
                }
            } else if (msg !is Image) {
                text += msg.contentToString()
            }
        }
        if (text.startsWith("\n")) text = text.substring(1)
        return text to entities
    }

    private suspend fun sendGroupMedias(
        chatId: String,
        replyId: Pair<Long, Int>?,
        medias: List<InputMediaPhoto>,
        chain: MessageChain,
        atEntities: List<MessageEntity>
    ) {
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
            try {
                if (list.size > 1) {
                    SendMediaGroup(chatId).apply {
                        this.replyToMessageId = replyId?.second
                        this.media = list
                    }.send().also { result ->
                        CacheService.cache(chain, result[0])
                    }
                } else if (list.size == 1) {
                    SendPhoto(chatId, list[0].media).apply {
                        this.replyToMessageId = replyId?.second
                        this.caption = list[0].caption
                    }.send().also { result ->
                        CacheService.cache(chain, result)
                    }
                }
            } catch (e: Exception) {
                log.error(e) { "Send group medias[$count] fail." }
                sendSimpleMedia(
                    chatId,
                    replyId,
                    medias.map { it.media.attachName },
                    medias[0].caption,
                    chain,
                    atEntities
                )
            }
            count++
        }
    }

    private suspend fun sendSimpleMedia(
        chatId: String,
        replyId: Pair<Long, Int>?,
        urls: List<String>,
        msg: String?,
        chain: MessageChain,
        atEntities: List<MessageEntity>,
        mask: String = "??????"
    ): moe.kurenai.tdlight.model.message.Message {
        var urlStr = ""
        for (url in urls) {
            urlStr += "[${mask.format2Markdown()}](${url.format2Markdown()})\n"
        }
        return SendMessage(chatId, "${msg?.fm2md()}$urlStr").apply {
            this.replyToMessageId = replyId?.second
            this.parseMode = ParseMode.MARKDOWN_V2
            if (atEntities.isNotEmpty()) entities = atEntities
        }.send().also { rec ->
            CacheService.cache(chain, rec)
        }
    }

    private fun cacheMsg(
        chain: MessageChain,
        recMsg: moe.kurenai.tdlight.model.message.Message,
        inputFile: InputFile? = null,
        qqFileId: String,
        imageSize: Long = 0L
    ) {
        if (inputFile != null && inputFile.isNew) {
            when {
                recMsg.hasDocument() -> {
                    recMsg.document!!.fileId
                }
                recMsg.hasAnimation() -> {
                    recMsg.animation!!.fileId
                }
                recMsg.hasPhoto() -> {
                    recMsg.photo!![0].fileId
                }
                else -> null
            }?.let { fileId ->
                CacheService.cacheFile(qqFileId, FileCache(fileId, imageSize))
            }
        }
        CacheService.cache(chain, recMsg)
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