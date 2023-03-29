package kurenai.imsyncbot.handler.tg

import jodd.util.StringPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.HttpUtil
import kurenai.imsyncbot.utils.suffix
import moe.kurenai.tdlight.exception.TelegramApiException
import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.media.PhotoSize
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.MessageEntity
import moe.kurenai.tdlight.request.GetFile
import moe.kurenai.tdlight.util.getLogger
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.isFile
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.io.path.exists
import moe.kurenai.tdlight.model.media.File as TelegramFile

class TgMessageHandler(
    configProperties: ConfigProperties,
    internal val bot: ImSyncBot
) : TelegramHandler {

    private val log = getLogger()

    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"

    init {
        if (configProperties.bot.tgMsgFormat.contains("\$msg")) tgMsgFormat = configProperties.bot.tgMsgFormat
        if (configProperties.bot.qqMsgFormat.contains("\$msg")) qqMsgFormat = configProperties.bot.qqMsgFormat
    }

    override suspend fun onEditMessage(message: Message): Int {
        if (!bot.groupConfig.tgQQ.containsKey(message.chat.id)) {
            return CONTINUE
        }
        if (Instant.ofEpochSecond(message.date).plusSeconds(TimeUnit.MINUTES.toSeconds(2)).isBefore(Instant.now())) {
            return END
        }
        CacheService.getQQByTg(message)?.recall()
        return onMessage(message)
    }

    @Throws(Exception::class)
    override suspend fun onMessage(message: Message): Int {
        if (message.isCommand()) {
            log.info("ignore command")
            return CONTINUE
        }
        if (!bot.groupConfig.tgQQ.containsKey(message.chat.id)) {
            log.info("ignore no configProperties group")
            return CONTINUE
        }
        if (bot.groupConfig.bannedGroups.contains(message.chat.id)) {
            log.info("ignore banned group")
            return CONTINUE
        }
        if (bot.userConfig.bannedIds.contains(message.from?.id)) {
            log.info("ignore banned id")
            return CONTINUE
        }

        val quoteMsgChain =
            message.replyToMessage?.let {
                CacheService.getQQByTg(it)
            }
        val groupId = quoteMsgChain?.source?.targetId ?: bot.groupConfig.tgQQ.getOrDefault(message.chat.id, bot.groupConfig.defaultQQGroup)
        if (groupId == 0L) return CONTINUE
        val group = bot.qq.qqBot.getGroup(groupId)
        if (null == group) {
            log.error("QQ group[$groupId] not found.")
            return CONTINUE
        }
        val senderId = message.from!!.id
        val isMaster = bot.userConfig.masterTg == senderId
        val senderName = getSenderName(message)
        val caption = message.caption ?: ""

        if ((message.from?.username == "GroupAnonymousBot" || isMaster) && (caption.contains("#nfwd") || message.text?.contains("#nfwd") == true)) {
            log.debug("No forward message.")
            return END
        }

        when {
            message.hasVoice() -> CoroutineScope(coroutineContext).launch {
                val voice = message.voice!!
                val file = getTgFile(voice.fileId, voice.fileUniqueId)
                uploadAndSend(group, file)
                if (!isMaster) group.sendMessage("Upload by $senderName.")
                if (caption.isNotBlank()) {
                    val builder = MessageChainBuilder()
                    formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, message.captionEntities, message.caption, builder)
                    CacheService.cache(group.sendMessage(builder.build()), message)
                }
            }

            message.hasVideo() -> CoroutineScope(coroutineContext).launch {
                val video = message.video!!
                val file = getTgFile(video.fileId, video.fileUniqueId)
                uploadAndSend(group, file, video.fileId + ".mp4")
                if (!isMaster) group.sendMessage("Upload by $senderName.")
                if (caption.isNotBlank()) {
                    val builder = MessageChainBuilder()
                    formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, message.captionEntities, message.caption, builder)
                    CacheService.cache(group.sendMessage(builder.build()), message)
                }
            }

            message.hasAnimation() -> CoroutineScope(coroutineContext).launch {
                val builder = MessageChainBuilder()
                val animation = message.animation!!
                val tgFile = getTgFile(animation.fileId, animation.fileUniqueId)
                if ((animation.fileSize ?: 0) > 800 * 1024) {
                    uploadAndSend(group, getTgFile(animation.fileId, "${animation.fileUniqueId}.mp4"))
                    if (!isMaster) group.sendMessage("Upload by $senderName.")
                    if (caption.isNotBlank()) {
                        formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, message.captionEntities, message.caption, builder)
                        CacheService.cache(group.sendMessage(builder.build()), message)
                    }
                } else {
                    BotUtil.mp42gif(animation.width, tgFile).let { gifFile ->
                        gifFile.toFile().toExternalResource().use {
                            builder.add(group.uploadImage(it))
                            formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, StringPool.EMPTY, builder)
                            CacheService.cache(group.sendMessage(builder.build()), message)
                        }
                    }
                }
            }


            message.hasSticker() -> {
                val sticker = message.sticker!!
                val builder = MessageChainBuilder()
                if (sticker.isVideo) {
                    BotUtil.mp42gif(sticker.width, getTgFile(sticker.fileId, sticker.fileUniqueId)).let { gifFile ->
                        gifFile.toFile().toExternalResource().use {
                            builder.add(group.uploadImage(it))
                            formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, StringPool.EMPTY, builder)
                            CacheService.cache(group.sendMessage(builder.build()), message)
                        }
                    }
                } else {
                    if (sticker.isAnimated) {
                        formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, sticker.emoji ?: "NaN", builder)
                    } else {
                        getImage(group, sticker.fileId, sticker.fileUniqueId)?.let(builder::add)
                        formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, StringPool.EMPTY, builder)
                    }
                    group.sendMessage(builder.build()).let {
                        CacheService.cache(it, message)
                    }
                }
            }

            message.hasDocument() -> CoroutineScope(coroutineContext).launch {
                val document = message.document!!
                val file = getTgFile(document.fileId, document.fileUniqueId)
                uploadAndSend(group, file, document.fileName ?: document.fileId)
                if (!isMaster) group.sendMessage("Upload by $senderName.")
                if (caption.isNotBlank()) {
                    val builder = MessageChainBuilder()
                    formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, message.captionEntities, message.caption, builder)
                    CacheService.cache(group.sendMessage(builder.build()), message)
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
                formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, message.captionEntities, message.caption, builder)
                CacheService.cache(group.sendMessage(builder.build()), message)
            }

            message.hasText() -> {
                val builder = MessageChainBuilder()
                formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, message.entities, message.text, builder)
                CacheService.cache(group.sendMessage(builder.build()), message)
            }
        }
        return CONTINUE
    }

    private suspend fun uploadAndSend(
        group: Group,
        file: TelegramFile,
        fileName: String = "${file.fileUniqueId}.${file.filePath.suffix()}",
    ) {
        var cacheFile = Path.of(file.filePath!!)
        if (!cacheFile.exists() || !cacheFile.isFile) {
            cacheFile = Path.of(BotUtil.getDocumentPath(fileName))
            if (!cacheFile.exists() || !cacheFile.isFile) {
                HttpUtil.download(file, cacheFile)
            }
        }

        return cacheFile.toFile().toExternalResource().use {
            group.files.uploadNewFile("/$fileName", it)
        }
    }

    private fun formatMsgAndQuote(
        quoteMsgChain: MessageChain?,
        isMaster: Boolean,
        id: Long,
        username: String,
        entities: List<MessageEntity>?,
        content: String?,
        builder: MessageChainBuilder,
    ) {
        var msg = content ?: ""
        entities?.forEach { entity ->
            val user = entity.user
            if (user != null) {
                bot.userConfig.items.find { it.tg == user.id }?.qq
            } else if (entity.type == MessageEntityType.MENTION) {
                bot.userConfig.items.find { it.username == username }?.qq
            } else {
                null
            }?.let {
                builder.add(At(it))
                msg = msg.removeRange(entity.offset, entity.offset + entity.length)
            }
        }
        formatMsgAndQuote(quoteMsgChain, isMaster, id, username, msg, builder)
    }

    private fun formatMsgAndQuote(
        quoteMsgChain: MessageChain?,
        isMaster: Boolean,
        id: Long,
        username: String,
        content: String,
        builder: MessageChainBuilder,
    ) {
        if (quoteMsgChain != null) {
            builder.add(quoteMsgChain.quote())
        }
        builder.add(formatMsg(isMaster, id, username, content))
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
        val tgFile = getTgFile(fileId, fileUniqueId)
        val image = if (tgFile.filePath?.endsWith("webp", true) == true) {
            BotUtil.webp2png(tgFile)
        } else {
            tgFile.filePath?.let { Path.of(it) }?.takeIf { it.exists() } ?: HttpUtil.download(tgFile, Path.of(BotUtil.getImagePath("$fileId.${tgFile.filePath.suffix()}")))
        }

        var ret: Image? = null
        try {
            image.toFile().toExternalResource().use {
                ret = group.uploadImage(it)
            }
        } catch (e: IOException) {
            log.error(e.message, e)
        }
        return ret
    }

    private suspend fun getTgFile(fileId: String, fileUniqueId: String): TelegramFile {
        try {
            return GetFile(fileId).send()
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
        return TelegramFile(fileId, fileUniqueId)
    }

    private suspend fun getSenderName(message: Message): String {
        val from = message.from!!
        if (from.username.equals("GroupAnonymousBot", true)) {
            return message.authorSignature ?: ""    // 匿名用头衔作为前缀，空头衔将会不添加前缀
        } else if (message.isChannelMessage() || from.id == 136817688L) { //tg 频道以及tg官方id不加前缀
            return ""
        } else {
            return bot.userConfig.idBindings[from.id]
                ?: bot.userConfig.usernameBindings[from.username]
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