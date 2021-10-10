package kurenai.imsyncbot.handler

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.BotUtil
import mu.KotlinLogging
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.event.events.FriendAvatarChangedEvent
import net.mamoe.mirai.event.events.FriendEvent
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.FriendMessageSyncEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendVoice
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.PhotoSize
import org.telegram.telegrambots.meta.api.objects.Update
import java.util.concurrent.ConcurrentHashMap

@Component
class PrivateChatHandler(
    val cacheService: CacheService,
    forwardHandlerProperties: ForwardHandlerProperties,
) {

    //TODO: 其他qq事件

    private val log = KotlinLogging.logger {}

    val privateChat = forwardHandlerProperties.privateChat
    val privateChatChannel = forwardHandlerProperties.privateChatChannel
    val locks = ConcurrentHashMap<Int, Object>()
    val newChannelLock = Object()
    val msgIds = HashMap<Int, Int>()
    private val picToFileSize = 500 * 1024
    private val contextMap = HashMap<Long, ExecutorCoroutineDispatcher>()

    suspend fun onFriendEvent(event: FriendEvent): Int {
//        val client = ContextHolder.telegramBotClient
        when (event) {
            is FriendMessageEvent -> {
                onFriendMessage(event.friend, event.message)
            }
            is FriendAvatarChangedEvent -> {
//                val messageId = cacheService.getPrivateChannelMessageId(event.friend.id)
//                InputMediaPhoto()
//                client.execute(EditMessageMedia())
            }
            is FriendMessageSyncEvent -> {
                onFriendMessage(event.friend, event.message)
            }
        }

        return END
    }

    suspend fun onFriendMessage(friend: Friend, message: MessageChain, isSync: Boolean = false) {
        val client = ContextHolder.telegramBotClient
        var messageId = message[QuoteReply.Key]?.source?.ids?.get(0)?.let { cacheService.getIdByQQ(it) }
        if (messageId == null) {
            synchronized(newChannelLock) {
                log.debug { "Locked by ${friend.id}" }
                messageId = message[QuoteReply.Key]?.source?.ids?.get(0)?.let { cacheService.getIdByQQ(it) }
                if (messageId == null) {
                    messageId = newChannel(friend) ?: return
                }
            }
        }
        for (msg in message) {
            when (msg) {
                is Image -> {
                    try {
                        val aspectRatio = msg.width.toFloat() / msg.height.toFloat()
                        var sendByFile = aspectRatio > 10 || aspectRatio < 0.1
                        val inputFile = cacheService.getFile(msg.imageId).let {
                            if (it == null) {
                                val file = BotUtil.downloadFile(msg.imageId, msg.queryUrl())
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
                            client.send(SendDocument(privateChat.toString(), inputFile).apply {
                                replyToMessageId = messageId
                                if (isSync) {
                                    caption = "同步消息"
                                }
                            })
                        } else {
                            client.send(SendPhoto(privateChat.toString(), inputFile).apply {
                                replyToMessageId = messageId
                                if (isSync) {
                                    caption = "同步消息"
                                }
                            })
                        }
                    } catch (e: Exception) {
                        log.debug { "fallback to send text" }
                        var content = "[图片](${msg.queryUrl()})"
                        if (isSync) content = "同步消息\n\n$content"
                        client.send(SendMessage(privateChat.toString(), content).apply {
                            replyToMessageId = messageId
                            parseMode = ParseMode.MARKDOWNV2
                        })
                    }
                }
                is FileMessage -> {
                    var content = "文件 ${msg.name}\n\n暂不支持私聊文件上传下载"
                    if (isSync) content = "同步消息\n\n$content"
                    client.send(SendMessage(privateChat.toString(), content).apply {
                        replyToMessageId = messageId
                    })
                }
                is OnlineAudio -> {
                    val file = BotUtil.downloadFile(msg.filename, msg.urlForDownload)
                    client.send(SendVoice(privateChat.toString(), InputFile(file)).apply {
                        replyToMessageId = messageId
                        if (isSync) {
                            caption = "同步消息"
                        }
                    })
                }
                else -> {
                    msg.contentToString().takeIf { it.isNotEmpty() }?.let {
                        val content = if (isSync) "同步消息\n\n$it" else it
                        client.send(SendMessage(privateChat.toString(), content).apply {
                            replyToMessageId = messageId
                        })
                    }
                }
            }?.let { rec ->
                message[OnlineMessageSource.Key]?.let { source ->
                    cacheService.cache(source, rec)
                }
            }
        }
    }

    suspend fun onPrivateChat(update: Update) {
        log.debug { "${this::class.java}#onPrivateChat" }
        if (update.message.from.id == 777000L && update.message.forwardFromChat.id == privateChatChannel) {
            onChannelForward(update)
        } else if (update.message.isReply) {
            //TODO: 无法回复
            val bot = ContextHolder.qqBot
            val client = ContextHolder.telegramBotClient
            val message = update.message
            val friendId = cacheService.getFriendId(getRootReplyMessageId(message.replyToMessage)) ?: return
            val friend = bot.getFriend(friendId) ?: return
            val builder = MessageChainBuilder()
            when {
                message.hasVoice() -> {
                    client.send(SendMessage(privateChat.toString(), "暂不支持私聊发送语音。").apply {
                        replyToMessageId = update.message.messageId
                    })
                }
                message.hasAudio() -> {
                    client.send(SendMessage(privateChat.toString(), "暂不支持私聊发送音频。").apply {
                        replyToMessageId = update.message.messageId
                    })
                }
                message.hasVideo() -> {
                    client.send(SendMessage(privateChat.toString(), "暂不支持私聊发送视频。").apply {
                        replyToMessageId = update.message.messageId
                    })
                }
                message.hasSticker() -> {
                    val sticker = message.sticker
                    if (sticker.isAnimated) {
                        builder.add(sticker.emoji)
                    } else {
                        BotUtil.getImage(friend, sticker.fileId, sticker.fileUniqueId)?.let(builder::add) ?: return
                    }
                }
                message.hasDocument() -> {
                    client.send(SendMessage(privateChat.toString(), "暂不支持私聊发送文件。").apply {
                        replyToMessageId = update.message.messageId
                    })
                    return
                }
                message.hasPhoto() -> {
                    message.photo.groupBy { it.fileId.substring(0, 40) }
                        .mapNotNull { (_: String, photoSizes: List<PhotoSize>) -> photoSizes.maxByOrNull { it.fileSize } }
                        .mapNotNull { BotUtil.getImage(friend, it.fileId, it.fileUniqueId) }.forEach(builder::add)
                    message.caption?.let {
                        builder.add(it)
                    }
                }
                message.hasText() -> {
                    builder.add(message.text)
                }
            }
            cacheService.cache(friend.sendMessage(builder.build()).source, update.message)
        }
    }

    fun newChannel(friend: Friend): Int? {
        val client = ContextHolder.telegramBotClient
        val bot = ContextHolder.qqBot
        var messageId = cacheService.getPrivateChannelMessageId(friend.id)
        if (messageId == null) {
            val rec = bot.getFriend(friend.id)?.let {
                client.execute(SendPhoto(privateChatChannel.toString(), InputFile(bot.getFriend(friend.id)?.avatarUrl)).apply {
                    caption = "昵称：#${friend.nick}\n备注：#${friend.remark}\n#id${friend.id}\n"
                })
            } ?: client.execute(SendMessage(privateChatChannel.toString(), "昵称：${friend.nick}\n备注：${friend.remark}\n#id${friend.id}\n")) as Message

            val lock = Object()
            locks[rec.messageId] = lock
            synchronized(lock) {
                log.debug { "Wait ${rec.messageId}" }
                lock.wait(10 * 1000L)
            }
            log.debug { "Wake up ${rec.messageId}, receive ${msgIds[rec.messageId]}" }
            messageId = msgIds.remove(rec.messageId) ?: return null
            cacheService.cachePrivateChat(friend.id, messageId)
        }
        return messageId
    }

    fun onChannelForward(update: Update) {
        log.debug { "${this::class.java}#onChannelForward" }
        val lock = locks.remove(update.message.forwardFromMessageId) ?: return
        msgIds[update.message.forwardFromMessageId] = update.message.messageId
        synchronized(lock) {
            lock.notifyAll()
            log.debug { "Notify ${update.message.forwardFromMessageId}" }
        }
    }

    private fun getRootReplyMessageId(message: Message): Int {
        return if (message.isReply) {
            getRootReplyMessageId(message.replyToMessage)
        } else {
            message.messageId
        }
    }

}