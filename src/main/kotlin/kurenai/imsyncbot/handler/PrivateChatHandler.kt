package kurenai.imsyncbot.handler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import kurenai.imsyncbot.service.CacheService
import mu.KotlinLogging
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.event.events.FriendEvent
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.sendTo
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File

@Component
class PrivateChatHandler(
    val cacheService: CacheService,
    forwardHandlerProperties: ForwardHandlerProperties,
) {

    private val log = KotlinLogging.logger {}

    val privateChat = forwardHandlerProperties.privateChat
    val privateChatChannel = forwardHandlerProperties.privateChatChannel
    val locks = HashMap<Int, Object>()
    val msgIds = HashMap<Int, Int>()

    suspend fun onFriendEvent(event: FriendEvent): Int {
        when (event) {
            is FriendMessageEvent -> {
                onFriendMessage(event)
            }
        }

        return END
    }

    suspend fun onFriendMessage(event: FriendMessageEvent) {
        val client = ContextHolder.telegramBotClient
        val bot = ContextHolder.qqBot
        var messageId = cacheService.getPrivateChatByQQ(event.friend.id)
        if (messageId == null) {
            val rec = bot.getFriend(event.friend.id)?.let {
                client.send(SendPhoto(privateChatChannel.toString(), InputFile(bot.getFriend(event.friend.id)?.avatarUrl)).apply {
                    caption = "昵称：#${event.friend.nick}\n备注：#${event.friend.remark}\n#id${event.friend.id}\n"
                })
            } ?: client.execute(SendMessage(privateChatChannel.toString(), "昵称：${event.friend.nick}\n备注：${event.friend.remark}\n#id${event.friend.id}\n")) as Message

            withContext(Dispatchers.IO) {
                val lock = Object()
                locks[rec.messageId] = lock
                synchronized(lock) {
                    log.debug { "Wait ${rec.messageId}" }
                    lock.wait(10 * 1000)
                }
            }
            log.debug { "Wake up ${rec.messageId}, receive ${msgIds[rec.messageId]}" }
            messageId = msgIds.remove(rec.messageId) ?: return
            cacheService.cachePrivateChat(event.friend.id, messageId)
        }
        for (msg in event.message) {
            when (msg) {
                is Image -> {
                    client.send(SendPhoto(privateChat.toString(), InputFile(msg.queryUrl())).apply {
                        replyToMessageId = messageId
                    })
                }
                else -> {
                    msg.contentToString().takeIf { it.isNotBlank() }?.let {
                        client.execute(SendMessage(privateChat.toString(), it).apply {
                            replyToMessageId = messageId
                        })
                    }
                }
            }
        }
    }

    suspend fun onPrivateChat(update: Update) {
        log.debug { "${this::class.java}#onPrivateChat" }
        if (update.message.from.id == 777000L && update.message.forwardFromChat.id == privateChatChannel) {
            onChannelForward(update)
        } else if (update.message.isReply) {
            val bot = ContextHolder.qqBot
            val message = update.message
            val friendId = cacheService.getPrivateChatByTG(getRootReplyMessageId(message.replyToMessage)) ?: return
            val friend = bot.getFriend(friendId) ?: return
            when {
                message.hasPhoto() -> {
                    for (photo in message.photo) {
                        val file = File(photo.filePath)
                        friend.uploadImage(file).sendTo(friend)
                    }
                }
                else -> {
                    friend.sendMessage(message.text)
                }
            }
        }
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