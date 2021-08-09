package kurenai.mybot.handler.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.mybot.CacheHolder
import kurenai.mybot.handler.Handler
import kurenai.mybot.handler.config.ForwardHandlerProperties
import kurenai.mybot.qq.QQBotClient
import kurenai.mybot.telegram.TelegramBotClient
import kurenai.mybot.utils.HttpUtil
import mu.KotlinLogging
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
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
import java.io.InputStream
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
    private val webpCmdPattern: String

    private val bindingName: Map<Long, String>
    private val picToFileSize = 500 * 1024
    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"
    private var qqTelegram = HashMap<Long, Long>()
    private var telegramQq = HashMap<Long, Long>()

    // tg 2 qq
    @Throws(Exception::class)
    override suspend fun handleMessage(client: TelegramBotClient, qqClient: QQBotClient, update: Update, message: Message): Boolean {
        val chatId = message.chatId
        val bot = qqClient.bot
        val quoteMsgSource =
            message.replyToMessage?.messageId
                ?.let(CacheHolder.TG_QQ_MSG_ID_CACHE::get)
                ?.let(CacheHolder.QQ_MSG_CACHE::get)
        val groupId = quoteMsgSource?.targetId ?: this.telegramQq.getOrDefault(chatId, properties.group.defaultQQ)
        if (groupId == 0L) return true
        val group = bot.getGroup(groupId)
        val senderId = message.from.id
        val isMaster = senderId == properties.masterOfTg
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
                val file = getFile(client, voice.fileId, voice.fileUniqueId)
                uploadAndSend(client, message, group, senderName, builder, file)
            }
            message.hasVideo() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val video = message.video
                val file = getFile(client, video.fileId, video.fileUniqueId)
                uploadAndSend(client, message, group, senderName, builder, file)
            }
            message.hasDocument() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val document = message.document
                val file = getFile(client, document.fileId, document.fileUniqueId)
                uploadAndSend(client, message, group, senderName, builder, file)
            }
            message.hasAnimation() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val animation = message.animation
                val file = getFile(client, animation.fileId, animation.fileUniqueId)
                uploadAndSend(client, message, group, senderName, builder, file)
            }
            message.hasSticker() -> {
                val builder = MessageChainBuilder()
                val sticker = message.sticker
                if (sticker.isAnimated) {
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, sticker.emoji, builder)
                    CacheHolder.cache(group.sendMessage(builder.build()).source, message)
                } else {
                    getImage(client, group, sticker.fileId, sticker.fileUniqueId)?.let(builder::add)
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                    CacheHolder.cache(group.sendMessage(builder.build()).source, message)
                }
            }
            message.hasPhoto() -> {
                val builder = MessageChainBuilder()
                message.photo.groupBy { it.fileId.substring(0, 40) }
                    .mapNotNull { (_: String, photoSizes: List<PhotoSize>) -> photoSizes.maxByOrNull { it.fileSize } }
                    .mapNotNull { getImage(client, group, it.fileId, it.fileUniqueId) }.forEach(builder::add)
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
    override suspend fun handleEditMessage(client: TelegramBotClient, qqClient: QQBotClient, update: Update, message: Message): Boolean {
        message.messageId?.let(CacheHolder.TG_QQ_MSG_ID_CACHE::get)?.let(CacheHolder.QQ_MSG_CACHE::get)?.recall()
        return handleMessage(client, qqClient, update, message)
    }


    // qq 2 tg
    @Throws(Exception::class)
    override suspend fun handleQQGroupMessage(
        client: QQBotClient,
        telegramBotClient: TelegramBotClient,
        event: GroupAwareMessageEvent
    ): Boolean {
        val group = event.group
        val sender = event.sender
        val senderName = formatUsername(
            bindingName[sender.id] ?: client.bot.getFriend(sender.id)?.remarkOrNick ?: (sender as Member).remarkOrNameCardOrNick
        )
        val messageChain = event.message
        val atAccount = AtomicLong(-100)
        val content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(client, group, atAccount, it) }
        val chatId = this.qqTelegram[group.id] ?: properties.group.defaultTelegram

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return true

        if (messageChain.contains(ForwardMessage.Key)) {
            return handleQQGroupForwardMessage(
                client,
                telegramBotClient,
                messageChain[ForwardMessage.Key]!!,
                group,
                chatId.toString(),
                senderName
            )
        }

        return handleQQGroupMessage(client, telegramBotClient, messageChain, event.group, chatId.toString(), sender.id, senderName)
    }

    private suspend fun uploadAndSend(
        client: TelegramBotClient,
        message: Message,
        group: Group,
        username: String,
        builder: MessageChainBuilder,
        file: org.telegram.telegrambots.meta.api.objects.File,
    ) {
        val systemFile = File(file.filePath)
        if (systemFile.exists() && systemFile.isFile) {
            builder.add(withContext(Dispatchers.IO) {
                group.filesRoot.uploadAndSend(systemFile).quote()
            })
            builder.add("upload from $username\n\n${message.caption}")
            CacheHolder.cache(group.sendMessage(builder.build()).source, message)
        } else {
            val cacheFile = File("./cache/file/${file.fileId.substring(0, 40)}.${getSuffix(file.filePath)}")
            if (!uploadFileAndSend(cacheFile, group, message)) {
                client.downloadFileAsStream(file).use { inputStream ->
                    FileUtils.copyInputStreamToFile(inputStream, cacheFile)
                    if (!uploadFileAndSend(cacheFile, group, message)) {
                        uploadStreamAndSend(inputStream, group, message)
                    }
                }
            }
        }
    }

    private suspend fun uploadFileAndSend(file: File, group: Group, message: Message): Boolean {
        return if (file.exists() && file.isFile) {
            withContext(Dispatchers.IO) {
                file.toExternalResource().use {
                    CacheHolder.cache(group.filesRoot.uploadAndSend(it).source, message)
                }
            }
            true
        } else
            false
    }

    private suspend fun uploadStreamAndSend(inputStream: InputStream, group: Group, message: Message) {
        inputStream.use { stream ->
            stream.toExternalResource().use { resource ->
                CacheHolder.cache(group.filesRoot.uploadAndSend(resource).source, message)
            }
        }
    }

    private suspend fun handleQQGroupMessage(
        client: QQBotClient,
        telegramBotClient: TelegramBotClient,
        messageChain: MessageChain,
        group: Group,
        chatId: String,
        senderId: Long,
        senderName: String,
    ): Boolean {
        log.debug("{}({}) - {}({}): {}", group.name, group.id, senderName, senderId, messageChain.contentToString())

        val source = messageChain[OnlineMessageSource.Key]
        val replyId = messageChain[QuoteReply.Key]?.source?.ids?.get(0)?.let(CacheHolder.QQ_TG_MSG_ID_CACHE::get)
        val atAccount = AtomicLong(-100)
        var content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(client, group, atAccount, it) }

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return true

        val isMaster = client.bot.id == senderId || properties.masterOfQq == senderId

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
                    InputMediaPhoto.builder().media(url).mediaName(image.imageId)
                        .build()
                }
            if (medias.isNotEmpty()) {
                medias[0].caption = msg
                val builder = SendMediaGroup.builder()
                replyId?.let(builder::replyToMessageId)
                telegramBotClient.execute(
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
                if (image.imageId.endsWith(".git")) {
                    val builder = SendAnimation.builder()
                    replyId?.let(builder::replyToMessageId)
                    telegramBotClient.execute(
                        builder
                            .chatId(chatId)
                            .caption(msg)
                            .animation(InputFile(url))
                            .build()
                    )
                } else {
                    val file = File(getImagePath(image.imageId))
                    if (!file.exists() || !file.isFile) FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                    if (file.length() > picToFileSize) {
                        val builder = SendDocument.builder()
                        replyId?.let(builder::replyToMessageId)
                        telegramBotClient.execute(
                            builder.caption(msg).chatId(chatId).document(InputFile(file)).thumb(InputFile(url)).build()
                        )
                    } else {
                        val builder = SendPhoto.builder()
                        replyId?.let(builder::replyToMessageId)
                        telegramBotClient.execute(builder.caption(msg).chatId(chatId).photo(InputFile(url)).build())
                    }
                }?.let { m ->
                    source?.let { CacheHolder.cache(source, m) }
                }
            }
        } else if (messageChain.contains(FileMessage)) {
            val downloadInfo = messageChain[FileMessage.Key]!!.toRemoteFile(group)?.getDownloadInfo() ?: return true
            val url: String = downloadInfo.url
            try {
                val file = File(getDocumentPath(downloadInfo.filename))
                if (!file.exists() || !file.isFile) FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                val filename: String = downloadInfo.filename.lowercase()
                if (filename.endsWith(".mkv") || filename.endsWith(".mp4")) {
                    telegramBotClient.execute(SendVideo.builder().video(InputFile(file)).chatId(chatId).caption(msg).build())
                } else if (filename.endsWith(".bmp") || filename.endsWith(".jpeg") || filename.endsWith(".jpg") || filename.endsWith(".png")) {
                    telegramBotClient.execute(
                        SendDocument.builder().document(InputFile(file)).thumb(InputFile(url)).chatId(chatId).caption(msg).build()
                    )
                } else {
                    telegramBotClient.execute(SendDocument.builder().document(InputFile(file)).chatId(chatId).caption(msg).build())
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
        } else {
            val builder = SendMessage.builder()
            replyId?.let(builder::replyToMessageId)
            telegramBotClient.execute(builder.chatId(chatId).text(msg).build())
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
    override suspend fun handleRecall(client: QQBotClient, telegramBotClient: TelegramBotClient, event: MessageRecallEvent): Boolean {
        val message = CacheHolder.QQ_TG_MSG_ID_CACHE[event.messageIds[0]]?.let(CacheHolder.TG_MSG_CACHE::get)
        message?.let {
            telegramBotClient.execute(
                DeleteMessage.builder().chatId(it.chatId.toString())
                    .messageId(it.messageId)
                    .build()
            )
        }
        return true
    }

    @Throws(java.lang.Exception::class)
    private suspend fun handleQQGroupForwardMessage(
        client: QQBotClient,
        telegramBotClient: TelegramBotClient,
        msg: ForwardMessage,
        group: Group,
        chatId: String,
        senderName: String
    ): Boolean {
        for ((senderId, _, forwardSenderName, messageChain) in msg.nodeList) {
            try {
                handleQQGroupMessage(
                    client,
                    telegramBotClient,
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

    private fun getSingleContent(client: QQBotClient, group: Group, atAccount: AtomicLong, msg: SingleMessage): String {
        return if (msg is At) {
            val target = msg.target
            if (target == atAccount.get()) return "" else atAccount.set(target)
            var name = bindingName[target]
                ?: client.bot.getFriend(target)?.remarkOrNick
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

    private fun getFile(client: TelegramBotClient, fileId: String, fileUniqueId: String): org.telegram.telegrambots.meta.api.objects.File {
        try {
            return client.execute(GetFile.builder().fileId(fileId).build())
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
        return File().apply {
            this.fileId = fileId
            this.fileUniqueId = fileUniqueId
        }
    }

    @Throws(TelegramApiException::class, IOException::class)
    private suspend fun getImage(client: TelegramBotClient, group: Group, fileId: String, fileUniqueId: String): Image? {
        val file = getFile(client, fileId, fileUniqueId)
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
                Runtime.getRuntime().exec(String.format(webpCmdPattern, webpFile.path, pngFile.path).replace("\\", "\\\\")).onExit()
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

    private fun formatContent(msg: String): String {
        return msg.replace("\\n", "\r\n")
    }

    private fun formatUsername(username: String): String {
        return username.replace("https://", "", true)
            .replace("http://", "", true)
            .replace(".", " .")
            .replace("/", "-")
    }

    private fun handleLongString(target: String, maxLength: Int): String {
        return if (target.length > maxLength) {
            target.substring(0, maxLength) + "..."
        } else target
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
        val encoderPath: String =
            if (System.getProperties().getProperty("os.name").equals("Linux", ignoreCase = true)) {
                File("./bin/dwebp").path
            } else {
                File("./bin/dwebp.exe").path
            }
        webpCmdPattern = "$encoderPath %s -o %s"
        bindingName = properties.member.bindingName
        if (properties.tgMsgFormat.contains("\$msg")) tgMsgFormat = properties.tgMsgFormat
        if (properties.qqMsgFormat.contains("\$msg")) qqMsgFormat = properties.qqMsgFormat

        properties.group.qqTelegram.takeIf { it.isNotEmpty() }?.map {
            this.qqTelegram.putIfAbsent(it.key, it.value)
            this.telegramQq.putIfAbsent(it.value, it.key)
        }
        properties.group.telegramQq.takeIf { it.isNotEmpty() }?.map {
            this.telegramQq.putIfAbsent(it.key, it.value)
            this.qqTelegram.putIfAbsent(it.value, it.key)
        }
    }
}