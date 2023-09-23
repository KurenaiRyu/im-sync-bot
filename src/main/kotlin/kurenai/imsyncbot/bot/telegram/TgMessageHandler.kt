package kurenai.imsyncbot.bot.telegram

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.Running
import kurenai.imsyncbot.command.CommandDispatcher
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.qqTgRepository
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.TelegramUtil.idString
import kurenai.imsyncbot.utils.TelegramUtil.userSender
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.message.sourceIds
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource

class TgMessageHandler(
    val bot: ImSyncBot
) {

    private val log = getLogger()

    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"

    private val traceEnabled = TelegramBot.log.isTraceEnabled

    init {
        if (bot.configProperties.bot.tgMsgFormat.contains("\$msg")) tgMsgFormat = bot.configProperties.bot.tgMsgFormat
        if (bot.configProperties.bot.qqMsgFormat.contains("\$msg")) qqMsgFormat = bot.configProperties.bot.qqMsgFormat
    }

    fun handle(update: Update) = bot.tg.launch(CoroutineName(update.idString())) {
        TelegramBot.log.trace("Incoming update: {}", update.toString().trim())
        val status = bot.tg.status.value
        if (status != Running) {
            TelegramBot.log.trace("Telegram bot status {}, do not handle", status.javaClass.simpleName)
        }
        runCatching {
            when (update) {
                is UpdateNewInlineQuery -> {
                    runCatching {
                        if (traceEnabled.not()) TelegramBot.log.debug(
                            "New inline query ({})[{}] from user {}, offset {}",
                            update.id,
                            update.query,
                            update.senderUserId,
                            update.offset
                        )
                    }.onFailure { it.printStackTrace() }
                }

                is UpdateNewMessage -> {
                    runCatching {
                        if (update.message.isOutgoing) {
                            if (traceEnabled.not()) TelegramBot.log.debug(
                                "New message(out going) {} from chat {}",
                                update.message.id,
                                update.message.chatId
                            )
                            return@launch
                        } else {
                            if (traceEnabled.not()) TelegramBot.log.debug(
                                "New message {} from chat {}",
                                update.message.id,
                                update.message.chatId
                            )
                        }
                    }.onFailure { it.printStackTrace() }

                    bot.tg.disposableHandlers.forEach {
                        if (it.handle(bot, update.message)) {
                            bot.tg.disposableHandlers.remove(it)
                            return@launch
                        }
                    }

                    val content = update.message.content
                    if (content is MessageText) {
                        val commandEntity = content.text.entities.firstOrNull { it.type is TextEntityTypeBotCommand }
                        if (commandEntity != null) {
                            CommandDispatcher.execute(bot, update.message, commandEntity)
                            return@launch
                        }
                    }

                    onMessage(update.message)
                }

                is UpdateMessageEdited -> {
                    runCatching {
                        if (traceEnabled.not()) TelegramBot.log.debug(
                            "Edited message {} from chat {}",
                            update.messageId,
                            update.chatId
                        )
                    }.onFailure { it.printStackTrace() }
                    bot.tg.editedMessages["${update.chatId}:${update.messageId}"] = false
                }

                is UpdateMessageContent -> {
                    runCatching {
                        if (traceEnabled.not()) TelegramBot.log.debug(
                            "Edited message content {} from chat {}",
                            update.messageId,
                            update.chatId
                        )
                    }.onFailure { it.printStackTrace() }
                    val key = "${update.chatId}:${update.messageId}"
                    if (bot.tg.editedMessages.contains(key)) {
                        bot.tg.editedMessages.invalidate(key)
                        onEditMessage(update)
                    }
                }

                is UpdateDeleteMessages -> {
                    runCatching {
                        if (traceEnabled.not()) {
                            TelegramBot.log.debug(
                                "Deleted messages {} from chat {}",
                                update.messageIds,
                                update.chatId,
                            )
                        }
                    }.onFailure { it.printStackTrace() }

                    if (update.isPermanent) {
                        MessageService.findQQMessageByDelete(update).map {
                            MessageChain.deserializeFromJsonString(it.json)
                        }.filter { //保证只撤回bot的
                            it.source.fromId == bot.qq.qqBot.id
                        }.forEach {
                            it.recall()
                        }
                    }
                }

                is UpdateMessageSendSucceeded -> {
                    runCatching {
                        if (traceEnabled.not()) {
                            TelegramBot.log.debug(
                                "Sent message {} -> {} to chat {}",
                                update.oldMessageId,
                                update.message.id,
                                update.message.chatId
                            )
                        }
                    }.onFailure { it.printStackTrace() }
                    bot.tg.pendingMessage.getIfPresent(update.oldMessageId)?.also {
                        bot.tg.pendingMessage.invalidate(update.oldMessageId)
                        if (it.isActive) {
                            log.trace("Resume {}", it)
                            it.resumeWith(Result.success(update.message))
                        } else {
                            qqTgRepository.findAllByTgMsgId(update.oldMessageId).forEach { old ->
                                old.tgMsgId = update.message.id
                                qqTgRepository.save(old)
                            }
                        }
                    }
                }

                is UpdateMessageSendFailed -> {
                    runCatching {
                        TelegramBot.log.error(
                            "Sent message {} -> {} to chat {} fail: {} {}",
                            update.oldMessageId,
                            update.message.id,
                            update.message.chatId,
                            update.errorCode,
                            update.errorMessage
                        )
                    }
                    bot.tg.pendingMessage.getIfPresent(update.oldMessageId)?.let {
                        bot.tg.pendingMessage.invalidate(update.oldMessageId)
                        if (it.isActive) {
                            it.resumeWith(Result.failure(BotException("[${update.errorCode}] ${update.errorMessage}")))
                        }
                    }
                }

                is UpdateConnectionState -> {
                    if (traceEnabled.not()) TelegramBot.log.debug(
                        "Update connection state: {}",
                        update.state::class.java
                    )
                }

                else -> {
                    if (traceEnabled.not()) TelegramBot.log.debug("Not handle {}", update::class.java)
                }
            }
            update
        }.onFailure { ex ->
            TelegramBot.log.error("Command handle error: ${ex.message}", ex)
            when (update) {
                is UpdateNewMessage -> update.message
                is UpdateMessageContent -> bot.tg.getMessage(update.chatId, update.messageId)
                else -> null
            }?.let {
                bot.tg.sendError(it, ex)
            }
        }
    }

    private suspend fun onEditMessage(update: UpdateMessageContent): Int {
        if (!bot.groupConfigService.tgQQ.containsKey(update.chatId)) {
            return CONTINUE
        }

        MessageService.findQQByTg(update.chatId, update.messageId)
            ?.takeIf { it.source.fromId == bot.qq.qqBot.id } //只撤回bot消息
            ?.recall()

        val message = bot.tg.getMessage(update.messageId, update.chatId)

        message.content = update.newContent

        return onMessage(message)
    }

    @Suppress("SameReturnValue")
    @Throws(Exception::class)
    suspend fun onMessage(message: TdApi.Message): Int {

        val userSender = message.userSender()
        if (!bot.groupConfigService.tgQQ.containsKey(message.chatId)) {
            log.info("ignore no configProperties group")
            return CONTINUE
        }
        if (bot.groupConfigService.bannedGroups.contains(message.chatId)) {
            log.info("ignore banned group")
            return CONTINUE
        }
        if (userSender != null && bot.userConfigService.bannedIds.contains(userSender.userId)) {
            log.info("ignore banned id")
            return CONTINUE
        }

        val quoteMsgChain = MessageService.findQQByTg(message.chatId, message.replyToMessageId)
        val groupId = quoteMsgChain?.source?.targetId ?: bot.groupConfigService.tgQQ.getOrDefault(
            message.chatId,
            bot.groupConfigService.defaultQQGroup
        )
        if (groupId == 0L) return CONTINUE
        val group = bot.qq.qqBot.getGroup(groupId)
        if (null == group) {
            log.error("QQ group[$groupId] not found.")
            return CONTINUE
        }
        val isMaster = bot.userConfigService.masterTg == userSender?.userId
        val senderName = getSenderName(message)

        val builder = MessageChainBuilder()
        quoteMsgChain?.let { builder.append(quoteMsgChain.quote()) }

        //TODO: 添加发送人前缀
        when (val content = message.content) {
            is MessageText -> {
                builder.append(content.text.text)
                group.sendMessage(builder.build())
            }

            is MessageSticker -> {
                val image = when (content.sticker.format.constructor) {
                    StickerFormatWebp.CONSTRUCTOR -> {
                        val file = bot.tg.downloadFile(content.sticker.sticker)
                        BotUtil.webp2png(file).toFile().toExternalResource().use {
                            group.uploadImage(it)
                        }
                    }

                    StickerFormatWebm.CONSTRUCTOR -> {
                        val file = bot.tg.downloadFile(content.sticker.sticker)
                        BotUtil.mp42gif(content.sticker.width, file).toFile().toExternalResource().use {
                            group.uploadImage(it)
                        }
                    }

                    else -> null
                }
                image?.let {
                    builder.append(it).build().sendTo(group)
                }
            }

            is MessagePhoto -> {
                val file = bot.tg.downloadFile(content.photo.sizes.maxBy { it.photo.size }.photo)
                java.io.File(file.local.path).toExternalResource().use {
                    builder.add(group.uploadImage(it))
                    builder.add(content.caption.text)
                    group.sendMessage(builder.build())
                }
            }

            is MessageAnimation -> {
                val file = bot.tg.downloadFile(content.animation.animation)
                val thumbnail = bot.tg.downloadFile(content.animation.thumbnail.file)
                if (content.animation.animation.size > 800 * 1024) {
                    java.io.File(thumbnail.local.path).toExternalResource().use { thum ->
                        java.io.File(file.local.path).toExternalResource().use { video ->
                            group.uploadShortVideo(
                                thumbnail = thum,
                                video = video,
                                fileName = content.animation.fileName,
                            )
                        }
                    }
                    null
                } else {
                    BotUtil.mp42gif(content.animation.width, file).toFile().toExternalResource().use {
                        builder.add(group.uploadImage(it))
                        builder.add(content.caption.text)
                        group.sendMessage(builder.build())
                    }
                }
            }

            is MessageVideo -> {
                val file = bot.tg.downloadFile(content.video.video)
                val thumbnail = bot.tg.downloadFile(content.video.thumbnail.file)

                java.io.File(thumbnail.local.path).toExternalResource().use { thumb ->
                    java.io.File(file.local.path).toExternalResource().use { video ->
                        group.uploadShortVideo(
                            thumbnail = thumb,
                            video = video,
                            fileName = content.video.fileName,
                        )
                    }
                }
                null
            }
//            is MessageAudio -> CoroutineScope(coroutineContext).launch {
//                content.audio.audio.id
//                val voice = message.voice!!
//                val file = getTgFile(voice.fileId, voice.fileUniqueId)
//                uploadAndSend(group, file)
//                if (!isMaster) group.sendMessage("Upload by $senderName.")
//                if (caption.isNotBlank()) {
//                    val builder = MessageChainBuilder()
//                    formatMsgAndQuote(
//                        quoteMsgChain,
//                        isMaster,
//                        senderId,
//                        senderName,
//                        message.captionEntities,
//                        message.caption,
//                        builder
//                    )
//                    MessageService.cache(group.sendMessage(builder.build()), message)
//                }
//            }
            else -> null
        }?.let {
            runCatching {
                if (it.sourceIds.isEmpty()) {
                    throw BotException("回执消息为空，可能被风控")
                } else {
                    bot.tg.launch {
                        MessageService.cache(it, message)
                    }
                }
            }.onFailure {
                log.warn("Cache message error", it)
            }
        }
        return CONTINUE
    }


//    @Throws(TelegramApiException::class, IOException::class)
//    private suspend fun getImage(contact: Contact, fileId: String, fileUniqueId: String): Image? {
//        val tgFile = getTgFile(fileId, fileUniqueId)
//        val image = if (tgFile.filePath?.endsWith("webp", true) == true) {
//            BotUtil.webp2png(tgFile)
//        } else {
//            tgFile.filePath?.let { Path.of(it) }?.takeIf { it.exists() } ?: HttpUtil.download(
//                tgFile,
//                Path.of(BotUtil.getImagePath("$fileId.${tgFile.filePath.suffix()}"))
//            )
//        }
//
//        var ret: Image? = null
//        try {
//            image.toFile().toExternalResource().use {
//                ret = contact.uploadImage(it)
//            }
//        } catch (e: IOException) {
//            log.error(e.message, e)
//        }
//        return ret
//    }

//    @Throws(Exception::class)
//    suspend fun onFriendMessage(message: Message): Int {
//        if (message.isCommand()) {
//            log.info("ignore command")
//            return CONTINUE
//        }
//
//        val quoteMsgChain =
//            message.replyToMessage?.let {
//                MessageService.getQQByTg(it)
//            }
//        val friendId = quoteMsgChain?.source?.targetId ?: bot.userConfig.chatIdFriends.getOrDefault(message.chat.id, 0)
//        if (friendId == 0L) return CONTINUE
//        val friend = bot.qq.qqBot.getFriend(friendId)
//        if (null == friend) {
//            log.error("QQ friend[$friendId] not found.")
//            return CONTINUE
//        }
//        val senderId = message.from!!.id
//        val isMaster = bot.userConfig.masterTg == senderId
//        val senderName = getSenderName(message)
//        val caption = message.caption ?: ""
//
//        if ((message.from?.username == "GroupAnonymousBot" || isMaster) && (caption.contains("#nfwd") || message.text?.contains(
//                "#nfwd"
//            ) == true)
//        ) {
//            log.debug("No forward message.")
//            return END
//        }
//
//        when {
//            message.hasSticker() -> {
//                val sticker = message.sticker!!
//                val builder = MessageChainBuilder()
//                if (sticker.isVideo) {
//                    BotUtil.mp42gif(sticker.width, getTgFile(sticker.fileId, sticker.fileUniqueId)).let { gifFile ->
//                        gifFile.toFile().toExternalResource().use {
//                            builder.add(friend.uploadImage(it))
//                            formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, StringPool.EMPTY, builder)
//                            MessageService.cache(friend.sendMessage(builder.build()), message)
//                        }
//                    }
//                } else {
//                    if (sticker.isAnimated) {
//                        formatMsgAndQuote(
//                            quoteMsgChain,
//                            isMaster,
//                            senderId,
//                            senderName,
//                            sticker.emoji ?: "NaN",
//                            builder
//                        )
//                    } else {
//                        getImage(friend, sticker.fileId, sticker.fileUniqueId)?.let(builder::add)
//                        formatMsgAndQuote(quoteMsgChain, isMaster, senderId, senderName, StringPool.EMPTY, builder)
//                    }
//                    friend.sendMessage(builder.build()).let {
//                        MessageService.cache(it, message)
//                    }
//                }
//            }
//
//            message.hasPhoto() -> {
//                val builder = MessageChainBuilder()
//                message.photo!!.groupBy { it.fileId.substring(0, 40) }
//                    .mapNotNull { (_: String, photoSizes: List<PhotoSize>) ->
//                        photoSizes.maxByOrNull {
//                            it.fileSize ?: 0
//                        }
//                    }
//                    .mapNotNull { getImage(friend, it.fileId, it.fileUniqueId) }.forEach(builder::add)
//                formatMsgAndQuote(
//                    quoteMsgChain,
//                    isMaster,
//                    senderId,
//                    senderName,
//                    message.captionEntities,
//                    message.caption,
//                    builder
//                )
//                MessageService.cache(friend.sendMessage(builder.build()), message)
//            }
//
//            message.hasText() -> {
//                val builder = MessageChainBuilder()
//                formatMsgAndQuote(
//                    quoteMsgChain,
//                    isMaster,
//                    senderId,
//                    senderName,
//                    message.entities,
//                    message.text,
//                    builder
//                )
//                MessageService.cache(friend.sendMessage(builder.build()), message)
//            }
//        }
//        return CONTINUE
//    }

//    private suspend fun uploadAndSend(
//        group: Group,
//        file: TelegramFile,
//        fileName: String = "${file.fileUniqueId}.${file.filePath.suffix()}",
//    ) {
//        var cacheFile = Path.of(file.filePath!!)
//        if (!cacheFile.exists() || cacheFile.isDirectory()) {
//            cacheFile = Path.of(BotUtil.getDocumentPath(fileName))
//            if (!cacheFile.exists() || cacheFile.isDirectory()) {
//                HttpUtil.download(file, cacheFile)
//            }
//        }
//
//        return cacheFile.toFile().toExternalResource().use {
//            group.files.uploadNewFile("/$fileName", it)
//        }
//    }
//
//    private fun formatMsgAndQuote(
//        quoteMsgChain: MessageChain?,
//        isMaster: Boolean,
//        id: Long,
//        username: String,
//        entities: List<MessageEntity>?,
//        content: String?,
//        builder: MessageChainBuilder,
//    ) {
//        var msg = content ?: ""
//        entities?.forEach { entity ->
//            val user = entity.user
//            if (user != null) {
//                bot.userConfig.items.find { it.tg == user.id }?.qq
//            } else if (entity.type == MessageEntityType.MENTION) {
//                bot.userConfig.items.find { it.username == username }?.qq
//            } else {
//                null
//            }?.let {
//                builder.add(At(it))
//                msg = msg.removeRange(entity.offset, entity.offset + entity.length)
//            }
//        }
//        formatMsgAndQuote(quoteMsgChain, isMaster, id, username, msg, builder)
//    }

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

//    private suspend fun getTgFile(fileId: String, fileUniqueId: String): TelegramFile {
//        try {
//            return GetFile(fileId).send()
//        } catch (e: TelegramApiException) {
//            log.error(e.message, e)
//        }
//        return TelegramFile(fileId, fileUniqueId)
//    }

    private suspend fun getSenderName(message: TdApi.Message): String {

        if (message.authorSignature?.isNotBlank() == true) return message.authorSignature
        if (message.isChannelPost) return ""

        return when (val sender = message.senderId) {
            is MessageSenderChat -> {
                ""
            }

            is MessageSenderUser -> {
                val user = bot.tg.getUser(sender.userId)
                val username = user.usernames?.activeUsernames?.firstOrNull()
                bot.userConfigService.idBindings[sender.userId]
                    ?: username?.let(bot.userConfigService.usernameBindings::get)
                    ?: let {
                        val fullName = "${user.firstName} ${user.lastName ?: ""}"
                        return fullName.ifBlank { username ?: "none" }
                    }
            }

            else -> ""
        }
    }
}