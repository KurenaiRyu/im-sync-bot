package kurenai.imsyncbot.handler.qq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.handler.Handler.Companion.BREAK
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.HttpUtil
import mu.KotlinLogging
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import org.apache.commons.io.FileUtils
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.io.IOException
import java.security.KeyManagementException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.atomic.AtomicLong

@Component
class QQForwardHandler(properties: ForwardHandlerProperties, private val cacheService: CacheService) : QQHandler {

    private val log = KotlinLogging.logger {}

    private val bindingName: Map<Long, String>
    private val picToFileSize = 500 * 1024
    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"

    init {
        bindingName = properties.member.bindingName
        if (properties.tgMsgFormat.contains("\$msg")) tgMsgFormat = properties.tgMsgFormat
        if (properties.qqMsgFormat.contains("\$msg")) qqMsgFormat = properties.qqMsgFormat
    }

    @Throws(Exception::class)
    override suspend fun onGroupMessage(event: GroupAwareMessageEvent): Int {
        val group = event.group
        val sender = event.sender
        val senderName = BotUtil.formatUsername(
            bindingName[sender.id] ?: ContextHolder.qqBot.getFriend(sender.id)?.remarkOrNick
            ?: (sender as Member).remarkOrNameCardOrNick
        )
        val messageChain = event.message
        val atAccount = AtomicLong(-100)
        val content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(group, atAccount, it) }
        val chatId = ContextHolder.qqTgBinding[group.id] ?: ContextHolder.defaultTgGroup

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return CONTINUE

        if (messageChain.contains(ForwardMessage.Key)) {
            return onGroupForwardMessage(
                messageChain[ForwardMessage.Key]!!,
                group,
                chatId.toString(),
                senderName
            )
        }

        return handleGroupMessage(messageChain, event.group, chatId.toString(), sender.id, senderName)
    }

    @Throws(TelegramApiException::class)
    override suspend fun onRecall(event: MessageRecallEvent): Int {
        val message = cacheService.getByQQ(event.messageIds[0])
        message?.let {
            ContextHolder.telegramBotClient.execute(
                DeleteMessage.builder().chatId(it.chatId.toString())
                    .messageId(it.messageId)
                    .build()
            )
        }
        return CONTINUE
    }

    private suspend fun handleGroupMessage(
        messageChain: MessageChain,
        group: Group,
        chatId: String,
        senderId: Long,
        senderName: String,
    ): Int {
        log.debug { "${group.name}(${group.id}) - $senderName($senderId): ${messageChain.contentToString()}" }

        val client = ContextHolder.telegramBotClient
        val source = messageChain[OnlineMessageSource.Key]
        val replyId = messageChain[QuoteReply.Key]?.source?.ids?.get(0)?.let { cacheService.getIdByQQ(it) }
        val atAccount = AtomicLong(-100)
        var content = messageChain.filter { it !is Image }.joinToString(separator = "") { getSingleContent(group, atAccount, it) }

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return CONTINUE

        val isMaster = ContextHolder.qqBot.id == senderId || ContextHolder.masterOfQQ == senderId

        if (isMaster && changeMsgFormatCmd(content)) {
            val demoContent = "demo msg."
            val demoMsg = qqMsgFormat
                .replace(BotUtil.NEWLINE_PATTERN, "\n")
                .replace(BotUtil.NAME_PATTERN, "demo username")
                .replace(BotUtil.ID_PATTERN, "123456789")
                .replace(BotUtil.MSG_PATTERN, demoContent)
            group.sendMessage("changed msg format\ne.g.\n$demoMsg")
            return BREAK
        }

        if (content.startsWith("\n")) {
            content = content.substring(1)
        }

        val msg = tgMsgFormat.replace(BotUtil.NEWLINE_PATTERN, "\n", true)
            .replace(BotUtil.ID_PATTERN, senderId.toString(), true)
            .replace(BotUtil.NAME_PATTERN, senderName.replace(" @", " "), true)
            .replace(BotUtil.MSG_PATTERN, content, true)

        if (chatId.isBlank() || chatId == "0") return CONTINUE
        val count = messageChain.filterIsInstance<Image>().count()
        if (count > 1) {
            val medias = messageChain.filterIsInstance<Image>()
                .map { image ->
                    val url = image.queryUrl()
                    val file = File(BotUtil.getImagePath(image.imageId))
                    if (!file.exists() || !file.isFile) {
                        withContext(Dispatchers.IO) {
                            FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                        }
                    }
                    InputMediaPhoto.builder().media(url).newMediaFile(file).isNewMedia(true).mediaName(image.imageId)
                        .build()
                }
            if (medias.isNotEmpty()) {
                medias[0].caption = msg
                val builder = SendMediaGroup.builder()
                replyId?.let(builder::replyToMessageId)
                client.execute(
                    builder
                        .medias(medias)
                        .chatId(chatId)
                        .build()
                ).let { result ->
                    source?.let { source -> cacheService.cache(source, result[0]) }
                }
            }
        } else if (count == 1) {
            messageChain[Image.Key]?.let { image ->
                val url: String = image.queryUrl()
                val file = File(BotUtil.getImagePath(image.imageId))
                if (!file.exists() || !file.isFile) {
                    withContext(Dispatchers.IO) {
                        FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                    }
                }
                val inputFile = InputFile(file)
                if (image.imageId.endsWith(".gif")) {
                    val builder = SendAnimation.builder()
                    replyId?.let(builder::replyToMessageId)
                    client.execute(
                        builder
                            .chatId(chatId)
                            .caption(msg)
                            .animation(inputFile)
                            .build()
                    )
                } else {
                    if (file.length() > picToFileSize) {
                        val builder = SendDocument.builder()
                        replyId?.let(builder::replyToMessageId)
                        client.execute(
                            builder.caption(msg).chatId(chatId).document(inputFile).thumb(inputFile).build()
                        )
                    } else {
                        val builder = SendPhoto.builder()
                        replyId?.let(builder::replyToMessageId)
                        client.execute(builder.caption(msg).chatId(chatId).photo(inputFile).build())
                    }
                }?.let { m ->
                    source?.let { cacheService.cache(source, m) }
                }
            }
        } else if (messageChain.contains(FileMessage.Key)) {
            val downloadInfo = messageChain[FileMessage.Key]!!.toRemoteFile(group)?.getDownloadInfo() ?: return CONTINUE
            val url: String = downloadInfo.url
            try {
                val file = File(BotUtil.getDocumentPath(downloadInfo.filename))
                if (!file.exists() || !file.isFile) withContext(Dispatchers.IO) {
                    FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                }
                val filename: String = downloadInfo.filename.lowercase()
                if (filename.endsWith(".mkv") || filename.endsWith(".mp4")) {
                    client.execute(SendVideo.builder().video(InputFile(file)).chatId(chatId).caption(msg).build())
                } else if (filename.endsWith(".bmp") || filename.endsWith(".jpeg") || filename.endsWith(".jpg") || filename.endsWith(".png")) {
                    client.execute(
                        SendDocument.builder().document(InputFile(file)).thumb(InputFile(url)).chatId(chatId).caption(msg).build()
                    )
                } else {
                    client.execute(SendDocument.builder().document(InputFile(file)).chatId(chatId).caption(msg).build())
                }
            } catch (e: NoSuchAlgorithmException) {
                log.error(e) { e.message }
            } catch (e: KeyStoreException) {
                log.error(e) { e.message }
            } catch (e: KeyManagementException) {
                log.error(e) { e.message }
            } catch (e: IOException) {
                log.error(e) { e.message }
            }
        } else if (messageChain.contains(Voice.Key)) {
            val voice = messageChain.get(Voice.Key)
            voice?.url?.let { url ->
                val file = File(url)
                if (!file.exists() || !file.isFile) {
                    withContext(Dispatchers.IO) {
                        FileUtils.writeByteArrayToFile(file, HttpUtil.download(url))
                    }
                }
                client.execute(SendVoice.builder().chatId(chatId).voice(InputFile(file)).build())
            }
        } else {
            val builder = SendMessage.builder()
            replyId?.let(builder::replyToMessageId)
            client.execute(builder.chatId(chatId).text(msg).build())
                .let { m ->
                    source?.let { cacheService.cache(source, m) }
                }
        }
        return CONTINUE
    }

    @Throws(java.lang.Exception::class)
    private suspend fun onGroupForwardMessage(
        msg: ForwardMessage,
        group: Group,
        chatId: String,
        senderName: String,
    ): Int {
        for ((senderId, _, forwardSenderName, messageChain) in msg.nodeList) {
            try {
                handleGroupMessage(
                    messageChain,
                    group,
                    chatId,
                    senderId,
                    "$senderName forward from $forwardSenderName"
                )
            } catch (e: java.lang.Exception) {
                log.error(e) { e.message }
            }
        }
        return CONTINUE
    }

    private fun getSingleContent(group: Group, atAccount: AtomicLong, msg: SingleMessage): String {
        return if (msg is At) {
            val target = msg.target
            if (target == atAccount.get()) return "" else atAccount.set(target)
            var name = bindingName[target]
                ?: ContextHolder.qqBot.getFriend(target)?.remarkOrNick
                ?: group.getMember(target)?.remarkOrNameCardOrNick?.let { BotUtil.formatUsername(it) }
                ?: target.toString()
            if (!name.startsWith("@")) {
                name = "@$name"
            }
            " $name "
        } else {
            msg.contentToString()
        }
    }

    private fun changeMsgFormatCmd(text: String): Boolean {
        return if (text.startsWith("/msg") && text.length > 4) {
            text.substring(5).takeIf { it.isNotBlank() }?.let {
                qqMsgFormat = it
                log.info { "Change qq message format: $qqMsgFormat" }
                true
            } ?: false
        } else false
    }

    override fun order(): Int {
        return 150
    }

}