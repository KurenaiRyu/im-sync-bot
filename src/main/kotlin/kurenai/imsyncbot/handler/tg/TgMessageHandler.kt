package kurenai.imsyncbot.handler.tg

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.ContextHolder.cacheService
import kurenai.imsyncbot.ContextHolder.config
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.GroupConfig.bannedGroups
import kurenai.imsyncbot.config.GroupConfig.tgQQ
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.telegram.sendSync
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.HttpUtil
import moe.kurenai.tdlight.exception.TelegramApiException
import moe.kurenai.tdlight.model.media.PhotoSize
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.request.GetFile
import mu.KotlinLogging
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.message.data.source
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File
import java.io.IOException
import javax.enterprise.context.ApplicationScoped

@ApplicationScoped
class TgMessageHandler : TelegramHandler {

    private val log = KotlinLogging.logger {}

    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"

    init {
        if (config.handler.tgMsgFormat.contains("\$msg")) tgMsgFormat = config.handler.tgMsgFormat
        if (config.handler.qqMsgFormat.contains("\$msg")) qqMsgFormat = config.handler.qqMsgFormat
    }

    override suspend fun onEditMessage(message: Message): Int {
        if (!tgQQ.containsKey(message.chat.id)) {
            return CONTINUE
        }
        cacheService.getQQByTg(message)?.recall()
        return onMessage(message)
    }

    @Throws(Exception::class)
    override suspend fun onMessage(message: Message): Int {
        if (message.isCommand()) {
            log.info { "ignore command" }
            return CONTINUE
        }
        if (!tgQQ.containsKey(message.chat.id)) {
            log.info { "ignore no config group" }
            return CONTINUE
        }
        if (bannedGroups.contains(message.chat.id)) {
            log.info { "ignore banned group" }
            return CONTINUE
        }
        if (UserConfig.bannedIds.contains(message.from?.id)) {
            log.info { "ignore banned id" }
            return CONTINUE
        }

        val bot = ContextHolder.qqBot
        val quoteMsgChain =
            message.replyToMessage?.let {
                cacheService.getQQByTg(it)
            }
        val groupId = quoteMsgChain?.source?.targetId ?: tgQQ.getOrDefault(message.chat.id, GroupConfig.defaultQQGroup)
        if (groupId == 0L) return CONTINUE
        val group = bot.getGroup(groupId)
        if (null == group) {
            log.error { "QQ group[$groupId] not found." }
            return CONTINUE
        }
        val senderId = message.from!!.id
        val isMaster = UserConfig.masterTg == senderId
        val senderName = getSenderName(message)
        val caption = message.caption ?: ""

        if ((message.from?.username == "GroupAnonymousBot" || isMaster) && (caption.contains("#nfwd") || message.text?.contains("#nfwd") == true)) {
            log.debug { "No forward message." }
            return END
        }

        when {
            message.hasVoice() -> {
                val voice = message.voice!!
                val file = getTgFile(voice.fileId, voice.fileUniqueId)
                uploadAndSend(group, file)
                if (caption.isNotBlank()) {
                    val builder = MessageChainBuilder()
                    formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, message.caption!!, builder)
                    cacheService.cache(group.sendMessage(builder.build()), message)
                }
            }
            message.hasVideo() -> {
                val video = message.video!!
                val file = getTgFile(video.fileId, video.fileUniqueId)
                uploadAndSend(group, file, video.fileName ?: video.fileId.plus(".mp4"))
                if (caption.isNotBlank()) {
                    val builder = MessageChainBuilder()
                    formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, message.caption!!, builder)
                    cacheService.cache(group.sendMessage(builder.build()), message)
                }
            }
            message.hasAnimation() -> {
                val builder = MessageChainBuilder()
                val animation = message.animation!!
                val tgFile = getTgFile(animation.fileId, animation.fileUniqueId)
                BotUtil.mp42gif(animation.fileId, tgFile)?.let { gifFile ->
                    gifFile.toExternalResource().use {
                        builder.add(group.uploadImage(it))
                        formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, "", builder)
                        cacheService.cache(group.sendMessage(builder.build()), message)
                    }
                }
            }
            message.hasSticker() -> {
                val sticker = message.sticker!!
                val builder = MessageChainBuilder()
                if (sticker.isVideo) {
                    BotUtil.mp42gif(sticker.fileId, getTgFile(sticker.fileId, sticker.fileUniqueId))?.let { gifFile ->
                        gifFile.toExternalResource().use {
                            builder.add(group.uploadImage(it))
                            formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, "", builder)
                            cacheService.cache(group.sendMessage(builder.build()), message)
                        }
                    }
                    return CONTINUE
                }
                if (sticker.isAnimated) {
                    formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, sticker.emoji ?: "NaN", builder)
                } else {
                    getImage(group, sticker.fileId, sticker.fileUniqueId)?.let(builder::add)
                    formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, "", builder)
                }
                group.sendMessage(builder.build()).let {
                    cacheService.cache(it, message)
                }
            }
            message.hasDocument() -> {
                val document = message.document!!
                val file = getTgFile(document.fileId, document.fileUniqueId)
                uploadAndSend(group, file, document.fileName ?: document.fileId)
                if (!isMaster) group.sendMessage("Upload by $senderName.")
                if (caption.isNotBlank()) {
                    val builder = MessageChainBuilder()
                    formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, caption, builder)
                    cacheService.cache(group.sendMessage(builder.build()), message)
                }
            }
            message.hasPhoto() -> {
                val builder = MessageChainBuilder()
                message.photo!!.groupBy { it.fileId.substring(0, 40) }
                    .mapNotNull { (_: String, photoSizes: List<PhotoSize>) ->
                        photoSizes.maxByOrNull {
                            it.fileSize ?: 0
                        }
                    }
                    .mapNotNull { getImage(group, it.fileId, it.fileUniqueId) }.forEach(builder::add)
                formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, caption, builder)
                cacheService.cache(group.sendMessage(builder.build()), message)
            }
            message.hasText() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, message.text!!, builder)
                cacheService.cache(group.sendMessage(builder.build()), message)
            }
        }
        return CONTINUE
    }

    private suspend fun uploadAndSend(
        group: Group,
        file: moe.kurenai.tdlight.model.media.File,
        fileName: String = "${file.fileId.substring(0, 40)}.${BotUtil.getSuffix(file.filePath)}",
    ) {
        var cacheFile = File(file.filePath!!)
        if (!cacheFile.exists() || !cacheFile.isFile) {
            cacheFile = File(BotUtil.getDocumentPath(fileName))
            if (!cacheFile.exists() || !cacheFile.isFile) {
                HttpUtil.download(file, cacheFile)
            }
        }

        return cacheFile.toExternalResource().use {
            group.files.uploadNewFile("/$fileName", it)
        }
    }

    private fun formatMsgAndQuote(
        quoteMsgChain: MessageChain?,
        isMaster: Boolean,
        id: Long,
        username: String,
        content: String,
        builder: MessageChainBuilder,
    ) {
        val msg = if (quoteMsgChain != null) {
            builder.add(quoteMsgChain.quote())
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
        val file = getTgFile(fileId, fileUniqueId)
        val suffix = BotUtil.getSuffix(file.filePath)
        val image = if (suffix.equals("webp", true)) {
            BotUtil.webp2png(file)
        } else {
            File(file.filePath!!).takeIf { it.exists() } ?: HttpUtil.download(file, File(BotUtil.getImagePath("$fileId.webp")))
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

    private suspend fun getTgFile(fileId: String, fileUniqueId: String): moe.kurenai.tdlight.model.media.File {
        try {
            return GetFile(fileId).sendSync()
        } catch (e: TelegramApiException) {
            log.error(e) { e.message }
        }
        return moe.kurenai.tdlight.model.media.File(fileId, fileUniqueId)
    }

    private fun getSenderName(message: Message): String {
        val from = message.from!!
        if (from.username.equals("GroupAnonymousBot", true)) {
            return message.authorSignature ?: ""    // 匿名用头衔作为前缀，空头衔将会不添加前缀
        } else if (message.isChannelMessage() || from.id == 136817688L) { //tg 频道以及tg官方id不加前缀
            return ""
        } else {
            return UserConfig.idBindings[from.id]
                ?: UserConfig.usernameBindings[from.username]
                ?: let {
                    val username = "${from.firstName} ${from.lastName ?: ""}"
                    return username.ifBlank { from.username ?: "none" }
                }
        }
    }

    override fun order(): Int {
        return 150
    }
}