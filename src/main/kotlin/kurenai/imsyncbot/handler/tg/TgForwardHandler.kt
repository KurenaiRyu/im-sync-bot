package kurenai.imsyncbot.handler.tg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.BotUtil
import mu.KotlinLogging
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.RemoteFile.Companion.sendFile
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.PhotoSize
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.io.IOException

@Component
class TgForwardHandler(
    properties: ForwardHandlerProperties,
    private val cacheService: CacheService,
) : TelegramHandler {

    private val log = KotlinLogging.logger {}

    private val bindingName: Map<Long, String>
    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"

    init {
        bindingName = properties.member.bindingName
        if (properties.tgMsgFormat.contains("\$msg")) tgMsgFormat = properties.tgMsgFormat
        if (properties.qqMsgFormat.contains("\$msg")) qqMsgFormat = properties.qqMsgFormat
    }

    override suspend fun onEditMessage(message: Message): Int {
        message.messageId?.let { cacheService.getByTg(it) ?: cacheService.getQQ(it) }?.recall()
        return onMessage(message)
    }

    @Throws(Exception::class)
    override suspend fun onMessage(message: Message): Int {
        if (message.isCommand) {
            return CONTINUE
        }

        val chatId = message.chatId
        val bot = ContextHolder.qqBot
        val quoteMsgSource =
            message.replyToMessage?.messageId?.let {
                cacheService.getByTg(it)
            }
        val groupId = quoteMsgSource?.targetId ?: ContextHolder.tgQQBinding.getOrDefault(chatId, ContextHolder.defaultQQGroup)
        if (groupId == 0L) return CONTINUE
        val group = bot.getGroup(groupId)
        if (null == group) {
            log.error { "QQ group[$groupId] not found." }
            return CONTINUE
        }
        val senderId = message.from.id
        val isMaster = senderId == ContextHolder.masterOfTg
        val senderName = getSenderName(message)
        val caption = message.caption ?: ""
        when {
            message.hasVoice() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val voice = message.voice
                val file = getTgFile(voice.fileId, voice.fileUniqueId)
                uploadAndSend(message, group, isMaster, senderId, senderName, builder, file)
            }
            message.hasVideo() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val video = message.video
                val file = getTgFile(video.fileId, video.fileUniqueId)
                uploadAndSend(message, group, isMaster, senderId, senderName, builder, file, video.fileName)
            }
            message.hasAnimation() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                val animation = message.animation
                val tgFile = getTgFile(animation.fileId, animation.fileUniqueId)
                BotUtil.mp42gif(animation.fileId, tgFile)?.let { gifFile ->
                    gifFile.toExternalResource().use {
                        builder.add(group.uploadImage(it))
                        cacheService.cache(group.sendMessage(builder.build()).source, message)
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
                    cacheService.cache(group.sendMessage(builder.build()).source, message)
                } else {
                    val file = getTgFile(document.fileId, document.fileUniqueId)
                    uploadAndSend(message, group, isMaster, senderId, senderName, builder, file, document.fileName)
                }
            }
            message.hasSticker() -> {
                val builder = MessageChainBuilder()
                val sticker = message.sticker
                if (sticker.isAnimated) {
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, sticker.emoji, builder)
                    cacheService.cache(group.sendMessage(builder.build()).source, message)
                } else {
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                    getImage(group, sticker.fileId, sticker.fileUniqueId)?.let(builder::add)
                    cacheService.cache(group.sendMessage(builder.build()).source, message)
                }
            }
            message.hasPhoto() -> {
                val builder = MessageChainBuilder()
                message.photo.groupBy { it.fileId.substring(0, 40) }
                    .mapNotNull { (_: String, photoSizes: List<PhotoSize>) -> photoSizes.maxByOrNull { it.fileSize } }
                    .mapNotNull { getImage(group, it.fileId, it.fileUniqueId) }.forEach(builder::add)
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
                cacheService.cache(group.sendMessage(builder.build()).source, message)
            }
            message.hasText() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, message.text, builder)
                cacheService.cache(group.sendMessage(builder.build()).source, message)
            }
        }
        return CONTINUE
    }

    private suspend fun uploadAndSend(
        message: Message,
        group: Group,
        isMaster: Boolean,
        id: Long,
        username: String,
        builder: MessageChainBuilder,
        file: org.telegram.telegrambots.meta.api.objects.File,
        fileName: String = "${file.fileId.substring(0, 40)}.${BotUtil.getSuffix(file.filePath)}",
    ) {
        var cacheFile = File(file.filePath)
        if (!cacheFile.exists() || !cacheFile.isFile) {
            cacheFile = File(BotUtil.getDocumentPath(fileName))
            if (!cacheFile.exists() || !cacheFile.isFile) {
                ContextHolder.telegramBotClient.downloadFile(file, cacheFile)
            }
        }

        withContext(Dispatchers.IO) {
            group.sendFile("/$fileName", cacheFile).quote()
        }
        message.caption?.let {
            builder.add(formatMsg(isMaster, id, username, it))
        }
        cacheService.cache(group.sendMessage(builder.build()).source, message)
    }

    private fun formatMsgAndQuote(
        quoteMsgSource: MessageSource?,
        isMaster: Boolean,
        id: Long,
        username: String,
        content: String,
        builder: MessageChainBuilder,
    ) {
        quoteMsgSource?.quote()?.let(builder::add)
        builder.add(formatMsg(isMaster, id, username, content))
    }

    private fun formatMsg(
        isMaster: Boolean,
        id: Long,
        username: String,
        content: String,
    ): String {
        return if (isMaster || username.isBlank()) {
            content
        } else { //非空名称或是非主人则添加前缀
            qqMsgFormat
                .replace(BotUtil.NEWLINE_PATTERN, "\n")
                .replace(BotUtil.NAME_PATTERN, username)
                .replace(BotUtil.ID_PATTERN, id.toString())
                .replace(BotUtil.MSG_PATTERN, content)
        }
    }

    @Throws(TelegramApiException::class, IOException::class)
    private suspend fun getImage(group: Group, fileId: String, fileUniqueId: String): Image? {
        val client = ContextHolder.telegramBotClient
        val file = getTgFile(fileId, fileUniqueId)
        val suffix = BotUtil.getSuffix(file.filePath)
        var image = File(file.filePath)
        if (!image.exists() || !image.isFile) {
            image = client.downloadFile(file)
        }
        if (suffix.equals("webp", true)) {
            val png = BotUtil.webp2png(file.fileId, image)
            if (png != null) image = png
        }

        var ret: Image? = null
        try {
            image.toExternalResource().use {
                ret = group.uploadImage(it)
            }
        } catch (e: IOException) {
            log.error(e) { e.message }
        }
        return ret
    }

    private fun getTgFile(fileId: String, fileUniqueId: String): org.telegram.telegrambots.meta.api.objects.File {
        try {
            return ContextHolder.telegramBotClient.execute(GetFile.builder().fileId(fileId).build())
        } catch (e: TelegramApiException) {
            log.error(e) { e.message }
        }
        return org.telegram.telegrambots.meta.api.objects.File().apply {
            this.fileId = fileId
            this.fileUniqueId = fileUniqueId
        }
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

    override fun order(): Int {
        return 100
    }
}