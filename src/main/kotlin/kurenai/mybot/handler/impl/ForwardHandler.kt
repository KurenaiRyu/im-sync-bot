package kurenai.mybot.handler.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.mybot.CacheHolder
import kurenai.mybot.ContextHolder
import kurenai.mybot.handler.Handler
import kurenai.mybot.handler.config.ForwardHandlerProperties
import kurenai.mybot.utils.HttpUtil
import mu.KotlinLogging
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.message.data.Voice
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.RemoteFile.Companion.sendFile
import org.apache.commons.io.FileUtils
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.*
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.io.IOException
import java.security.KeyManagementException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicLong

@Component
@ConditionalOnProperty(prefix = "bot.handler.forward", name = ["enable"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(
    ForwardHandlerProperties::class
)
class ForwardHandler(private val properties: ForwardHandlerProperties) : Handler {

    private val log = KotlinLogging.logger {}
    private val webp2pngCmdPattern: String
    private val mp42gifCmdPattern: String

    private val bindingName: Map<Long, String>
    private val picToFileSize = 500 * 1024
    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"

    // tg 2 qq
    @Throws(Exception::class)
    override suspend fun handleTgMessage(update: Update, message: Message): Boolean {
        val chatId = message.chatId
        val bot = ContextHolder.qqBotClient.bot
        val quoteMsgSource =
            message.replyToMessage?.messageId
                ?.let(CacheHolder.TG_QQ_MSG_ID_CACHE::get)
                ?.let(CacheHolder.QQ_MSG_CACHE::get)
        val groupId = quoteMsgSource?.targetId ?: ContextHolder.tgQQBinding.getOrDefault(chatId, ContextHolder.defaultQQGroup)
        if (groupId == 0L) return true
        val group = bot.getGroup(groupId)
        val senderId = message.from.id
        val isMaster = senderId == ContextHolder.masterOfTg
        val senderName = getSenderName(message)
        if (group == null) {
            log.error("QQ group[$groupId] not found.")
            return true
        }
        val caption = message.caption ?: ""
        when {
            message.hasVoice() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val voice = message.voice
                val file = getTgFile(voice.fileId, voice.fileUniqueId)
                uploadAndSend(message, group, senderName, builder, file)
            }
            message.hasVideo() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val video = message.video
                val file = getTgFile(video.fileId, video.fileUniqueId)
                uploadAndSend(message, group, senderName, builder, file, video.fileName)
            }
            message.hasAnimation() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val animation = message.animation
                val tgFile = getTgFile(animation.fileId, animation.fileUniqueId)
                mp42gif(animation.fileId, tgFile)?.let { gifFile ->
                    var ret: Image? = null
                    try {
                        gifFile.toExternalResource().use {
                            builder.add(group.uploadImage(it))
                            CacheHolder.cache(group.sendMessage(builder.build()).source, message)
                        }
                    } catch (e: IOException) {
                        log.error(e.message, e)
                    }
                }
            }
            message.hasDocument() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val document = message.document
                if (document.fileName.endsWith("jpe") || document.fileName.endsWith("jpeg") || document.fileName.endsWith("png") || document.fileName.endsWith(
                        "bmp"
                    )
                ) {
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                    getImage(group, document.fileId, document.fileUniqueId)?.let(builder::add)
                    CacheHolder.cache(group.sendMessage(builder.build()).source, message)
                } else {
                    val file = getTgFile(document.fileId, document.fileUniqueId)
                    uploadAndSend(message, group, senderName, builder, file, document.fileName)
                }
            }
            message.hasSticker() -> {
                val builder = MessageChainBuilder()
                val sticker = message.sticker
                if (sticker.isAnimated) {
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, sticker.emoji, builder)
                    CacheHolder.cache(group.sendMessage(builder.build()).source, message)
                } else {
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                    getImage(group, sticker.fileId, sticker.fileUniqueId)?.let(builder::add)
                    CacheHolder.cache(group.sendMessage(builder.build()).source, message)
                }
            }
            message.hasPhoto() -> {
                val builder = MessageChainBuilder()
                message.photo.groupBy { it.fileId.substring(0, 40) }
                    .mapNotNull { (_: String, photoSizes: List<PhotoSize>) -> photoSizes.maxByOrNull { it.fileSize } }
                    .mapNotNull { getImage(group, it.fileId, it.fileUniqueId) }.forEach(builder::add)
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                CacheHolder.cache(group.sendMessage(builder.build()).source, message)
            }
            message.hasText() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, message.text, builder)
                CacheHolder.cache(group.sendMessage(builder.build()).source, message)
            }
        }
        return true
    }

    @Throws(Exception::class)
    override suspend fun handleTgEditMessage(update: Update, message: Message): Boolean {
        message.messageId?.let(CacheHolder.TG_QQ_MSG_ID_CACHE::get)?.let(CacheHolder.QQ_MSG_CACHE::get)?.recall()
        return handleTgMessage(update, message)
    }


    // qq 2 tg
    @Throws(Exception::class)
    override suspend fun handleQQGroupMessage(
        event: GroupAwareMessageEvent
    ): Boolean {
        val group = event.group
        val sender = event.sender
        val senderName = formatUsername(
            bindingName[sender.id] ?: ContextHolder.qqBotClient.bot.getFriend(sender.id)?.remarkOrNick
            ?: (sender as Member).remarkOrNameCardOrNick
        )
        val messageChain = event.message
        val atAccount = AtomicLong(-100)
        val content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(group, atAccount, it) }
        val chatId = ContextHolder.qqTgBinding[group.id] ?: ContextHolder.defaultTgGroup

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return true

        if (messageChain.contains(ForwardMessage.Key)) {
            return handleQQGroupForwardMessage(
                messageChain[ForwardMessage.Key]!!,
                group,
                chatId.toString(),
                senderName
            )
        }

        return doHandleQQGroupMessage(messageChain, event.group, chatId.toString(), sender.id, senderName)
    }

    private suspend fun uploadAndSend(
        message: Message,
        group: Group,
        username: String,
        builder: MessageChainBuilder,
        file: org.telegram.telegrambots.meta.api.objects.File,
        fileName: String = "${file.fileId.substring(0, 40)}.${getSuffix(file.filePath)}"
    ) {
        var cacheFile = File(file.filePath)
        if (!cacheFile.exists() || !cacheFile.isFile) {
            cacheFile = File("./cache/file/$fileName")
            if (!cacheFile.exists() || !cacheFile.isFile) {
                ContextHolder.telegramBotClient.downloadFile(file, cacheFile)
            }
        }

        builder.add(withContext(Dispatchers.IO) {
            group.sendFile("/$fileName", cacheFile).quote()
        })
        val caption = message.caption?.let { "\n\n$it" } ?: ""
        builder.add("upload from $username$caption")
        CacheHolder.cache(group.sendMessage(builder.build()).source, message)
    }

    private suspend fun doHandleQQGroupMessage(
        messageChain: MessageChain,
        group: Group,
        chatId: String,
        senderId: Long,
        senderName: String,
    ): Boolean {
        log.debug("{}({}) - {}({}): {}", group.name, group.id, senderName, senderId, messageChain.contentToString())

        val client = ContextHolder.telegramBotClient
        val source = messageChain[OnlineMessageSource.Key]
        val replyId = messageChain[QuoteReply.Key]?.source?.ids?.get(0)?.let(CacheHolder.QQ_TG_MSG_ID_CACHE::get)
        val atAccount = AtomicLong(-100)
        var content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(group, atAccount, it) }

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return true

        val isMaster = ContextHolder.qqBotClient.bot.id == senderId || ContextHolder.masterOfQQ == senderId

        if (isMaster && handleChangeQQMsgFormatCmd(content)) {
            val demoContent = "demo msg."
            val demoMsg = qqMsgFormat
                .replace(NEWLINE_PATTERN, "\n")
                .replace(NAME_PATTERN, "demo username")
                .replace(ID_PATTERN, "123456789")
                .replace(MSG_PATTERN, demoContent)
            group.sendMessage("changed msg format\ne.g.\n$demoMsg")
            return false
        }

        if (content.startsWith("\n")) {
            content = content.substring(1)
        }

        val msg = tgMsgFormat.replace(NEWLINE_PATTERN, "\n", true)
            .replace(ID_PATTERN, senderId.toString(), true)
            .replace(NAME_PATTERN, senderName.replace(" @", " "), true)
            .replace(MSG_PATTERN, content, true)

        if (chatId.isBlank() || chatId == "0") return true
        val count = messageChain.filterIsInstance<Image>().count()
        if (count > 1) {
            val medias = messageChain.filterIsInstance<Image>()
                .map { image ->
                    val url = image.queryUrl()
                    val file = File(getImagePath(image.imageId))
                    if (!file.exists() || !file.isFile) {
                        withContext(Dispatchers.IO) {
                            FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                        }
                    }
                    InputMediaPhoto.builder().media(url).newMediaFile(file).isNewMedia(true).mediaName(image.imageId)
                        .build()
                }
            if (medias.isNotEmpty()) {
                medias[0].caption = msg
                val builder = SendMediaGroup.builder()
                replyId?.let(builder::replyToMessageId)
                client.execute(
                    builder
                        .medias(medias)
                        .chatId(chatId)
                        .build()
                ).let { result ->
                    source?.let { source -> CacheHolder.cache(source, result[0]) }
                }
            }
        } else if (count == 1) {
            messageChain[Image.Key]?.let { image ->
                val url: String = image.queryUrl()
                val file = File(getImagePath(image.imageId))
                if (!file.exists() || !file.isFile) {
                    withContext(Dispatchers.IO) {
                        FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                    }
                }
                val inputFile = InputFile(file)
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
                    if (file.length() > picToFileSize) {
                        val builder = SendDocument.builder()
                        replyId?.let(builder::replyToMessageId)
                        client.execute(
                            builder.caption(msg).chatId(chatId).document(inputFile).thumb(inputFile).build()
                        )
                    } else {
                        val builder = SendPhoto.builder()
                        replyId?.let(builder::replyToMessageId)
                        client.execute(builder.caption(msg).chatId(chatId).photo(inputFile).build())
                    }
                }?.let { m ->
                    source?.let { CacheHolder.cache(source, m) }
                }
            }
        } else if (messageChain.contains(FileMessage.Key)) {
            val downloadInfo = messageChain[FileMessage.Key]!!.toRemoteFile(group)?.getDownloadInfo() ?: return true
            val url: String = downloadInfo.url
            try {
                val file = File(getDocumentPath(downloadInfo.filename))
                if (!file.exists() || !file.isFile) withContext(Dispatchers.IO) {
                    FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                }
                val filename: String = downloadInfo.filename.lowercase()
                if (filename.endsWith(".mkv") || filename.endsWith(".mp4")) {
                    client.execute(SendVideo.builder().video(InputFile(file)).chatId(chatId).caption(msg).build())
                } else if (filename.endsWith(".bmp") || filename.endsWith(".jpeg") || filename.endsWith(".jpg") || filename.endsWith(".png")) {
                    client.execute(
                        SendDocument.builder().document(InputFile(file)).thumb(InputFile(url)).chatId(chatId).caption(msg).build()
                    )
                } else {
                    client.execute(SendDocument.builder().document(InputFile(file)).chatId(chatId).caption(msg).build())
                }
            } catch (e: NoSuchAlgorithmException) {
                log.error(e.message, e)
            } catch (e: KeyStoreException) {
                log.error(e.message, e)
            } catch (e: KeyManagementException) {
                log.error(e.message, e)
            } catch (e: IOException) {
                log.error(e.message, e)
            }
        } else if (messageChain.contains(Voice.Key)) {
            val voice = messageChain.get(Voice.Key)
            voice?.url?.let { url ->
                val file = File(url)
                if (!file.exists() || !file.isFile) {
                    withContext(Dispatchers.IO) {
                        FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                    }
                }
                client.execute(SendVoice.builder().chatId(chatId).voice(InputFile(file)).build())
            }
        } else {
            val builder = SendMessage.builder()
            replyId?.let(builder::replyToMessageId)
            client.execute(builder.chatId(chatId).text(msg).build())
                .let { m ->
                    source?.let { CacheHolder.cache(source, m) }
                }
        }
        return true
    }

    private fun handleChangeQQMsgFormatCmd(text: String): Boolean {
        return if (text.startsWith("/msg") && text.length > 4) {
            text.substring(5).takeIf { it.isNotBlank() }?.let {
                qqMsgFormat = it
                log.info("Change qq message format: $qqMsgFormat")
                true
            } ?: false
        } else false
    }

    @Throws(TelegramApiException::class)
    override suspend fun handleQQRecall(event: MessageRecallEvent): Boolean {
        val message = CacheHolder.QQ_TG_MSG_ID_CACHE[event.messageIds[0]]?.let(CacheHolder.TG_MSG_CACHE::get)
        message?.let {
            ContextHolder.telegramBotClient.execute(
                DeleteMessage.builder().chatId(it.chatId.toString())
                    .messageId(it.messageId)
                    .build()
            )
        }
        return true
    }

    @Throws(java.lang.Exception::class)
    private suspend fun handleQQGroupForwardMessage(
        msg: ForwardMessage,
        group: Group,
        chatId: String,
        senderName: String
    ): Boolean {
        for ((senderId, _, forwardSenderName, messageChain) in msg.nodeList) {
            try {
                doHandleQQGroupMessage(
                    messageChain,
                    group,
                    chatId,
                    senderId,
                    "$senderName forward from $forwardSenderName"
                )
            } catch (e: java.lang.Exception) {
                log.error(e.message, e)
            }
        }
        return true
    }

    private fun getSingleContent(group: Group, atAccount: AtomicLong, msg: SingleMessage): String {
        return if (msg is At) {
            val target = msg.target
            if (target == atAccount.get()) return "" else atAccount.set(target)
            var name = bindingName[target]
                ?: ContextHolder.qqBotClient.bot.getFriend(target)?.remarkOrNick
                ?: group.getMember(target)?.remarkOrNameCardOrNick?.let { formatUsername(it) }
                ?: target.toString()
            if (!name.startsWith("@")) {
                name = "@$name"
            }
            " $name "
        } else {
            msg.contentToString()
        }
    }

    override fun order(): Int {
        return 100
    }

    private fun getTgFile(fileId: String, fileUniqueId: String): org.telegram.telegrambots.meta.api.objects.File {
        try {
            return ContextHolder.telegramBotClient.execute(GetFile.builder().fileId(fileId).build())
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
        return File().apply {
            this.fileId = fileId
            this.fileUniqueId = fileUniqueId
        }
    }

    @Throws(TelegramApiException::class, IOException::class)
    private suspend fun getImage(group: Group, fileId: String, fileUniqueId: String): Image? {
        val client = ContextHolder.telegramBotClient
        val file = getTgFile(fileId, fileUniqueId)
        val suffix = getSuffix(file.filePath)
        var image = File(file.filePath)
        if (!image.exists() || !image.isFile) {
            image = client.downloadFile(file)
        }
        if (suffix.equals("webp", true)) {
            val png = webp2png(file.fileId, image)
            if (png != null) image = png
        }

        var ret: Image? = null
        try {
            image.toExternalResource().use {
                ret = group.uploadImage(it)
            }
        } catch (e: IOException) {
            log.error(e.message, e)
        }
        return ret
    }

    private fun getSenderName(message: Message): String {
        val from = message.from
        if (from.userName.equals("GroupAnonymousBot", true)) {
            return message.authorSignature ?: ""    // 匿名用头衔作为前缀，空头衔将会不添加前缀
        } else {
            return bindingName[from.id] ?: let {
                val username = "${from.firstName} ${from.lastName ?: ""}"
                return username.ifBlank { from.userName ?: "none" }
            }
        }
    }

    private fun webp2png(id: String, webpFile: File): File? {
        val pngFile = File(getImagePath("$id.png"))
        if (pngFile.exists()) return pngFile
        pngFile.parentFile.mkdirs()
        try {
            val future =
                Runtime.getRuntime().exec(String.format(webp2pngCmdPattern, webpFile.path, pngFile.path).replace("\\", "\\\\")).onExit()
            if (future.get().exitValue() >= 0 || pngFile.exists()) return pngFile
        } catch (e: IOException) {
            log.error(e.message, e)
        } catch (e: ExecutionException) {
            log.error(e.message, e)
        } catch (e: InterruptedException) {
            log.error(e.message, e)
        }
        return null
    }

    private fun mp42gif(id: String, tgFile: org.telegram.telegrambots.meta.api.objects.File): File? {
        val gifFile = File(getImagePath("$id.gif"))
        if (gifFile.exists()) return gifFile
        var mp4File = File(tgFile.filePath)
        if (!mp4File.exists()) mp4File = ContextHolder.telegramBotClient.downloadFile(tgFile)
        gifFile.parentFile.mkdirs()
        try {
            val future =
                Runtime.getRuntime().exec(String.format(mp42gifCmdPattern, mp4File.path, gifFile.path).replace("\\", "\\\\")).onExit()
            if (future.get().exitValue() >= 0 || gifFile.exists()) return gifFile
        } catch (e: IOException) {
            log.error(e.message, e)
        } catch (e: ExecutionException) {
            log.error(e.message, e)
        } catch (e: InterruptedException) {
            log.error(e.message, e)
        }
        return null
    }

    private fun formatUsername(username: String): String {
        return username.replace("https://", "", true)
            .replace("http://", "", true)
            .replace(".", " .")
            .replace("/", "-")
    }

    private fun getSuffix(path: String?): String {
        return path?.substring(path.lastIndexOf('.').plus(1)) ?: ""
    }

    private fun formatMsgAndQuote(
        quoteMsgSource: OnlineMessageSource?,
        isMaster: Boolean,
        id: Long,
        username: String,
        content: String,
        builder: MessageChainBuilder
    ) {
        quoteMsgSource?.quote()?.let(builder::add)
        if (isMaster || username.isBlank()) {
            builder.add(content)
        } else { //非空名称或是非主人则添加前缀
            val handledMsg = qqMsgFormat
                .replace(NEWLINE_PATTERN, "\n")
                .replace(NAME_PATTERN, username)
                .replace(ID_PATTERN, id.toString())
                .replace(MSG_PATTERN, content)
            builder.add(handledMsg)
        }
    }

    private fun getImagePath(imageName: String): String {
        return "./cache/img/$imageName"
    }

    private fun getDocumentPath(docName: String): String {
        return "./cache/doc/$docName"
    }

    companion object {
        const val NAME_PATTERN = "\$name"
        const val MSG_PATTERN = "\$msg"
        const val ID_PATTERN = "\$id"
        const val NEWLINE_PATTERN = "\$newline"
    }

    init {
        webp2pngCmdPattern = "dwebp %s -o %s"
        mp42gifCmdPattern = "ffmpeg -i %s %s"
        bindingName = properties.member.bindingName
        if (properties.tgMsgFormat.contains("\$msg")) tgMsgFormat = properties.tgMsgFormat
        if (properties.qqMsgFormat.contains("\$msg")) qqMsgFormat = properties.qqMsgFormat
    }
}