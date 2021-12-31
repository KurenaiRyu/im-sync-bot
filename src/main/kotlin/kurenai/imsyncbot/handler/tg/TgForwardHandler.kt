package kurenai.imsyncbot.handler.tg

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.config.GroupConfig.tgQQ
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
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

    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"

    init {
        if (properties.tgMsgFormat.contains("\$msg")) tgMsgFormat = properties.tgMsgFormat
        if (properties.qqMsgFormat.contains("\$msg")) qqMsgFormat = properties.qqMsgFormat
    }

    override suspend fun onEditMessage(message: Message): Int {
        if (!tgQQ.containsKey(message.chatId)) {
            return CONTINUE
        }
        cacheService.getQQByTg(message)?.recall()
        return onMessage(message)
    }

    @Throws(Exception::class)
    override suspend fun onMessage(message: Message): Int {
        if (message.isCommand) {
            return CONTINUE
        }
        if (!tgQQ.containsKey(message.chatId)) {
            return CONTINUE
        }

        val chatId = message.chatId
        val bot = ContextHolder.qqBot
        val quoteMsgSource =
            message.replyToMessage?.let {
                cacheService.getQQByTg(it)
            }
        val groupId = quoteMsgSource?.targetId ?: tgQQ.getOrDefault(chatId, ContextHolder.defaultQQGroup)
        if (groupId == 0L) return CONTINUE
        val group = bot.getGroup(groupId)
        if (null == group) {
            log.error { "QQ group[$groupId] not found." }
            return CONTINUE
        }
        val senderId = message.from.id
        val isMaster = ContextHolder.masterOfTg.contains(senderId)
        val senderName = getSenderName(message)
        val caption = message.caption ?: ""

        if ((message.from.userName == "GroupAnonymousBot" || isMaster) && (caption.contains("#nfwd") || message.text?.contains("#nfwd") == true)) {
            log.debug { "No forward message." }
            return END
        }

        when {
            message.hasVoice() -> {
                val voice = message.voice
                val file = getTgFile(voice.fileId, voice.fileUniqueId)
                uploadAndSend(message, group, file)
            }
            message.hasVideo() -> {
                val video = message.video
                val file = getTgFile(video.fileId, video.fileUniqueId)
                uploadAndSend(message, group, file, video.fileName)
            }
            message.hasAnimation() -> {
                val builder = MessageChainBuilder()
                val animation = message.animation
                val tgFile = getTgFile(animation.fileId, animation.fileUniqueId)
                BotUtil.mp42gif(animation.fileId, tgFile)?.let { gifFile ->
                    gifFile.toExternalResource().use {
                        builder.add(group.uploadImage(it))
                        cacheService.cache(group.sendMessage(builder.build()).source, message)
                    }
                }
            }
            message.hasSticker() -> {
                val sticker = message.sticker
                val builder = MessageChainBuilder()
                if (sticker.isAnimated) {
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, sticker.emoji, builder)
                } else {
                    getImage(group, sticker.fileId, sticker.fileUniqueId)?.let(builder::add)
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, "", builder)
                }
                group.sendMessage(builder.build()).let {
                    cacheService.cache(it.source, message)
                }
            }
            message.hasDocument() -> {
                val document = message.document
                val file = getTgFile(document.fileId, document.fileUniqueId)
                uploadAndSend(message, group, file, document.fileName)
                if (!isMaster) group.sendMessage("Upload by $senderName.")
                if (caption.isNotBlank()) {
                    val builder = MessageChainBuilder()
                    formatMsgAndQuote(quoteMsgSource, isMaster, senderId, senderName, caption, builder)
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

        return cacheFile.toExternalResource().use { group.files.uploadNewFile("/$fileName", it) }
    }

    private fun formatMsgAndQuote(
        quoteMsgSource: MessageSource?,
        isMaster: Boolean,
        id: Long,
        username: String,
        content: String,
        builder: MessageChainBuilder,
    ) {
        val msg = if (quoteMsgSource != null) {
            builder.add(quoteMsgSource.quote())
            content.takeIf { it.isNotEmpty() } ?: " "
        } else {
            content
        }
        builder.add(formatMsg(isMaster, id, username, msg))
    }

    private fun formatMsg(
        isMaster: Boolean,
        id: Long,
        username: String,
        content: String,
    ): String {
        return if (isMaster || username.isEmpty()) {
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
        val image = if (suffix.equals("webp", true)) {
            BotUtil.webp2png(file)
        } else {
            File(file.filePath).takeIf { it.exists() } ?: client.downloadFile(file, File(BotUtil.getImagePath("$fileId.webp")))
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

    private suspend fun getTgFile(fileId: String, fileUniqueId: String): org.telegram.telegrambots.meta.api.objects.File {
        try {
            return ContextHolder.telegramBotClient.send(GetFile.builder().fileId(fileId).build())
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
        } else if (message.isChannelMessage || from.id == 136817688L) { //tg 频道以及tg官方id不加前缀
            return ""
        } else {
            return UserConfig.idBindings[from.id]
                ?: UserConfig.usernameBindings[from.userName]
                ?: let {
                    val username = "${from.firstName} ${from.lastName ?: ""}"
                    return username.ifBlank { from.userName ?: "none" }
                }
        }
    }

    override fun order(): Int {
        return 150
    }
}