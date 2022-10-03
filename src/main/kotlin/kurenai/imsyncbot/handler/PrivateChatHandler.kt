package kurenai.imsyncbot.handler

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ConfigProperties
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
import moe.kurenai.tdlight.request.message.SendDocument
import moe.kurenai.tdlight.request.message.SendMessage
import moe.kurenai.tdlight.request.message.SendPhoto
import moe.kurenai.tdlight.request.message.SendVoice
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.event.events.FriendAvatarChangedEvent
import net.mamoe.mirai.event.events.FriendEvent
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.FriendMessageSyncEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.sourceMessage
import org.apache.logging.log4j.LogManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PrivateChatHandler(
    configProperties: ConfigProperties
) {

    //TODO: 其他qq事件
    private val log = LogManager.getLogger()

    val privateChat = configProperties.handler.privateChat
    val privateChatChannel = configProperties.handler.privateChatChannel
    val newChannelLock = ReentrantLock()
    val newChannelCondition = newChannelLock.newCondition()
    private val picToFileSize = configProperties.handler.picToFileSize * 1024 * 1024

    private var currentMessage: Message? = null

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
            chain[QuoteReply.Key]?.let { CacheService.getTgIdByQQ(it.source.targetId, it.source.ids[0]) }?.second
        if (replyMsgId == null) {
            replyMsgId = getStartMsg(friend)
        }
        for (msg in chain) {
            when (msg) {
                is Image -> {
                    try {
                        val aspectRatio = msg.width.toFloat() / msg.height.toFloat()
                        var sendByFile = aspectRatio > 10 || aspectRatio < 0.1 || msg.width > 1920 || msg.height > 1920
                        val inputFile = CacheService.getFile(msg.imageId).let {
                            if (it == null) {
                                val file = BotUtil.downloadImg(msg.imageId, msg.queryUrl())
                                if (!sendByFile && file.length() > picToFileSize) {
                                    sendByFile = true
                                }
                                InputFile(file)
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
                    val file = BotUtil.downloadDoc(msg.filename, msg.urlForDownload)
                    SendVoice(privateChat.toString(), InputFile(file)).apply {
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
            CacheService.cache(friend.sendMessage(builder.build()).sourceMessage, message)
        }
    }

    suspend fun getStartMsg(friend: Friend): Int? {
        val qqBot = getBotOrThrow().qq.qqBot
        var messageId = CacheService.getPrivateChannelMessageId(friend.id)
        var count = 0

        while (!newChannelLock.tryLock() && count < 3) {
            count++
            delay(5000)
        }
        if (count >= 3) {
            if (newChannelLock.isLocked) {
                newChannelLock.unlock()
            }
        } else if (newChannelLock.isHeldByCurrentThread) {
            kotlin.runCatching {
                qqBot.getFriend(friend.id)?.let {
                    SendPhoto(
                        privateChatChannel.toString(),
                        InputFile(qqBot.getFriend(friend.id)?.avatarUrl!!)
                    ).apply {
                        caption = "昵称：#${friend.nick}\n备注：#${friend.remark}\n#id${friend.id}\n"
                    }.send()
                }
                log.debug("Wait for new channel message")
                newChannelCondition.await(1, TimeUnit.MINUTES)
                log.debug("New channel message received: ${currentMessage?.messageId}")
                currentMessage?.messageId?.let {
                    messageId = it
                    CacheService.cachePrivateChat(friend.id, it)
                }
            }.onFailure {
                log.debug("getStartMsg error", it)
            }
            newChannelLock.unlock()
        }
        return messageId
    }

    suspend fun onChannelForward(update: Update) {
        coroutineScope {
            launch {
                log.debug("${this::class.java}#onChannelForward")
                currentMessage = update.message!!
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

}