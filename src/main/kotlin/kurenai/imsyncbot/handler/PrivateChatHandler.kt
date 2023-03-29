package kurenai.imsyncbot.handler

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.BotUtil
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.media.PhotoSize
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.*
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.event.events.FriendAvatarChangedEvent
import net.mamoe.mirai.event.events.FriendEvent
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.FriendMessageSyncEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.fileSize

class PrivateChatHandler(
    configProperties: ConfigProperties
) {

    //TODO: 其他qq事件
    private val log = LogManager.getLogger()

    val privateChat = configProperties.bot.privateChat
    val privateChatChannel = configProperties.bot.privateChatChannel
    val newChannelLock = ReentrantLock()
    val newChannelCondition = newChannelLock.newCondition()
    private val picToFileSize = configProperties.bot.picToFileSize * 1024 * 1024

    private var messageMap = mutableMapOf<String, Message>()

    private val privateChannelMessageIdCache = WeakHashMap<Long, Int>()

    suspend fun onFriendEvent(event: FriendEvent): Int {
        when (event) {
            is FriendMessageEvent -> {
                onFriendMessage(event.friend, event.message)
            }

            is FriendAvatarChangedEvent -> {
//                val messageId = CacheService.getPrivateChannelMessageId(event.friend.id)
//                InputMediaPhoto()
//                client.execute(EditMessageMedia())
            }
            is FriendMessageSyncEvent -> {
                if (event.friend.id != getBotOrThrow().userConfig.masterQQ) {
                    onFriendMessage(event.friend, event.message, true)
                }
            }
        }

        return END
    }

    suspend fun onFriendMessage(friend: Friend, chain: MessageChain, isSync: Boolean = false) {
        var replyMsgId =
            (chain[QuoteReply.Key]?.let { CacheService.getTgIdByQQ(it.source.targetId, it.source.ids[0]) }?.second) ?: kotlin.run {
                // 没有引用消息则需要找到最开始的第一条消息
                getStartMsg(friend) ?: throw BotException("找不到引用的消息或是第一条消息")
            }
        kotlin.runCatching {
            GetMessageInfo(privateChat.toString(), replyMsgId).send()
        }.recover {
            log.warn(it.message, it)
            if (it is BotException && it.message?.endsWith("message not found") == true) {
                log.warn("Remove error private channel message id")
                CacheService.removePrivateChannelMessageId(friend.id)
            }
            replyMsgId = getStartMsg(friend) ?: throw BotException("找不到引用的消息或是第一条消息")
        }.onSuccess {
            for (msg in chain) {
                when (msg) {
                    is Image -> {
                        try {
                            val aspectRatio = msg.width.toFloat() / msg.height.toFloat()
                            var sendByFile = aspectRatio > 10 || aspectRatio < 0.1 || msg.width > 1920 || msg.height > 1920
                            val inputFile = CacheService.getFile(msg.imageId).let {
                                if (it == null) {
                                    val path = BotUtil.downloadImg(msg.imageId, msg.queryUrl())
                                    if (!sendByFile && path.fileSize() > picToFileSize) {
                                        sendByFile = true
                                    }
                                    InputFile(path.toFile())
                                } else {
                                    if (!sendByFile && it.fileSize > picToFileSize) {
                                        sendByFile = true
                                    }
                                    InputFile(it.fileId)
                                }
                            }
                            if (sendByFile) {
                                SendDocument(privateChat.toString(), inputFile).apply {
                                    replyToMessageId = replyMsgId
                                    if (isSync) {
                                        caption = "同步消息"
                                    }
                                }.send()
                            } else {
                                SendPhoto(privateChat.toString(), inputFile).apply {
                                    replyToMessageId = replyMsgId
                                    if (isSync) {
                                        caption = "同步消息"
                                    }
                                }.send()
                            }
                        } catch (e: Exception) {
                            log.debug("fallback to send text")
                            var content = "[图片](${msg.queryUrl()})"
                            if (isSync) content = "同步消息\n\n$content"
                            SendMessage(privateChat.toString(), content).apply {
                                replyToMessageId = replyMsgId
                                parseMode = ParseMode.MARKDOWN_V2
                            }.send()
                        }
                    }

                    is FileMessage -> {
                        var content = "文件 ${msg.name}\n\n暂不支持私聊文件上传下载"
                        if (isSync) content = "同步消息\n\n$content"
                        SendMessage(privateChat.toString(), content).apply {
                            replyToMessageId = replyMsgId
                        }.send()
                    }

                    is OnlineAudio -> {
                        val path = BotUtil.downloadDoc(msg.filename, msg.urlForDownload)
                        SendVoice(privateChat.toString(), InputFile(path.toFile())).apply {
                            replyToMessageId = replyMsgId
                            if (isSync) {
                                caption = "同步消息"
                            }
                        }.send()
                    }

                    else -> {
                        msg.contentToString().takeIf { it.isNotEmpty() }?.let {
                            val content = if (isSync) "同步消息\n\n$it" else it
                            SendMessage(privateChat.toString(), content).apply {
                                replyToMessageId = replyMsgId
                            }.send()
                        }
                    }
                }?.also { rec ->
                    CacheService.cache(chain, rec)
                }
            }
        }
    }

    suspend fun onPrivateChat(update: Update) {
        val bot = getBotOrThrow()
        val message = update.message!!
        if (message.from?.id == 777000L && message.forwardFromChat?.id == privateChatChannel) {
            onChannelForward(update)
        } else if (message.isReply()) {
            val rootReplyMessageId = message.getRootReplyMessageId()
            val quoteMsgSource = message.replyToMessage?.let { CacheService.getQQByTg(it) }
            val friendId = CacheService.getFriendId(rootReplyMessageId) ?: run {
                SendMessage(privateChat.toString(), "无法通过引用消息找到qq好友: $rootReplyMessageId").apply {
                    replyToMessageId = message.messageId
                }.send()
                return
            }
            val friend = bot.qq.qqBot.getFriend(friendId) ?: run {
                SendMessage(privateChat.toString(), "找不到qq好友: $friendId").apply {
                    replyToMessageId = message.messageId
                }.send()
                return
            }
            val builder = MessageChainBuilder()
            quoteMsgSource?.let { builder.add(QuoteReply(it)) }
            when {
                message.hasVoice() -> {
                    SendMessage(privateChat.toString(), "暂不支持私聊发送语音").apply {
                        replyToMessageId = message.messageId
                    }.send()
                }
                message.hasAudio() -> {
                    SendMessage(privateChat.toString(), "暂不支持私聊发送音频").apply {
                        replyToMessageId = message.messageId
                    }.send()
                }
                message.hasVideo() -> {
                    SendMessage(privateChat.toString(), "暂不支持私聊发送视频").apply {
                        replyToMessageId = message.messageId
                    }.send()
                }
                message.hasSticker() -> {
                    val sticker = message.sticker!!
                    if (sticker.isAnimated) {
                        builder.add(sticker.emoji ?: "NaN")
                    } else {
                        BotUtil.getImage(friend, sticker.fileId, sticker.fileUniqueId)?.let(builder::add) ?: return
                    }
                }
                message.hasDocument() -> {
                    SendMessage(privateChat.toString(), "暂不支持私聊发送文件").apply {
                        replyToMessageId = message.messageId
                    }.send()
                    return
                }
                message.hasPhoto() -> {
                    message.photo!!.groupBy { it.fileId.substring(0, 40) }
                        .mapNotNull { (_: String, photoSizes: List<PhotoSize>) ->
                            photoSizes.maxByOrNull {
                                it.fileSize ?: 0
                            }
                        }
                        .mapNotNull { BotUtil.getImage(friend, it.fileId, it.fileUniqueId) }.forEach(builder::add)
                    message.caption?.let {
                        builder.add(it)
                    }
                }
                message.hasText() -> {
                    builder.add(message.text!!)
                }
            }
            CacheService.cache(friend.sendMessage(builder.build()), message)
        }
    }

    suspend fun getStartMsg(friend: Friend): Int? {
        val bot = getBotOrThrow()
        val qqBot = bot.qq.qqBot
        var messageId = getPrivateChannelMessageIdCache(friend.id)

        if (messageId == null) {
            newChannelLock.withLock {
                runBlocking(bot.coroutineContext) {
                    messageId = getPrivateChannelMessageIdCache(friend.id)
                    if (messageId == null) {
                        kotlin.runCatching {
                            qqBot.getFriend(friend.id)?.let {
                                SendPhoto(
                                    privateChatChannel.toString(),
                                    InputFile(qqBot.getFriend(friend.id)?.avatarUrl!!)
                                ).apply {
                                    caption = "昵称：#${friend.nick}\n备注：#${friend.remark}\n#id${friend.id}"
                                }.send()
                            }
                            log.debug("Wait for new channel message")
                            newChannelCondition.await(20, TimeUnit.SECONDS)
                            val message = messageMap.remove(friend.id.toString()) ?: throw BotException("获取不到好友开头信息")
                            log.debug("New channel message received: ${message.messageId}")
                            message.messageId!!.let {
                                messageId = it
                                CacheService.cachePrivateChat(friend.id, it)
                            }
                        }.onFailure {
                            log.debug("getStartMsg error", it)
                        }.getOrThrow()
                    }
                }
            }
        }
        return messageId
    }

    suspend fun onChannelForward(update: Update) {
        coroutineScope {
            launch {
                log.debug("${this::class.java}#onChannelForward")
                val message = update.message!!
                val caption = message.caption ?: return@launch
                messageMap[caption.substringAfterLast("#id")] = update.message!!
                newChannelLock.withLock {
                    newChannelCondition.signalAll()
                }
                log.debug("Signal with message id: ${update.message?.messageId}")
            }
        }
    }

    private suspend fun Message.getRootReplyMessageId(): Int {
        return if (this.isReply()) {
            CacheService.getTg(this.chat.id, this.replyToMessage!!.messageId!!)?.getRootReplyMessageId()
                ?: this.messageId!!
        } else {
            this.messageId!!
        }
    }

    private suspend fun getPrivateChannelMessageIdCache(friendId: Long): Int? {
        return privateChannelMessageIdCache[friendId] ?: CacheService.getPrivateChannelMessageId(friendId)
    }

}