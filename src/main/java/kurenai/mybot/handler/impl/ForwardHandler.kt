package kurenai.mybot.handler.impl

import kotlinx.coroutines.runBlocking
import kurenai.mybot.CacheHolder
import kurenai.mybot.QQBotClient
import kurenai.mybot.TelegramBotClient
import kurenai.mybot.handler.Handler
import kurenai.mybot.handler.config.ForwardHandlerProperties
import mu.KotlinLogging
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.MessageSource.Key.recall
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.*
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.media.InputMediaAnimation
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@Component
@ConditionalOnProperty(prefix = "bot.handler.forward", name = ["enable"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(
    ForwardHandlerProperties::class
)
class ForwardHandler(private val properties: ForwardHandlerProperties) : Handler {

    private val log = KotlinLogging.logger {}
    private val webpCmdPattern: String
    private val cachePool = Executors.newFixedThreadPool(1)

    //TODO 最好将属性都提取出来，最少也要把第二层属性提取出来，不然每次判空
    private val bindingName: Map<Long, String>
    private var tgMsgFormat = "{name}: {msg}"

    // tg 2 qq
    @Throws(Exception::class)
    override fun handleMessage(client: TelegramBotClient, qqClient: QQBotClient, update: Update?, message: Message): Boolean {
        val chatId = message.chatId
        val bot = qqClient.bot
        val quoteMsgSource =
            message.replyToMessage.messageId
                .let(CacheHolder.TG_QQ_MSG_ID_CACHE::get)
                ?.let(CacheHolder.QQ_MSG_CACHE::get)
        val groupId = quoteMsgSource?.targetId ?: properties.group.telegramQq.getOrDefault(chatId, properties.group.defaultQQ)
        if (groupId == 0L) return true
        val group = bot.getGroup(groupId)
        val isMaster = message.from?.id == properties.master
        val username = getUsername(message)
        if (group == null) {
            log.error("QQ group[$groupId] not found.")
            return true
        }
        //        if (message.hasDocument()) {
//            var document = message.getDocument();
//            handleFile(client, group, document.getFileId(), document.getFileUniqueId());
//        } else if (message.hasVideo()) {
//            var video = message.getVideo();
//            handleFile(client, group, video.getFileId(), video.getFileUniqueId());
//        }
//        if (message.hasAnimation()) {
//            Animation recMsg = message.getAnimation();
//            File      file   = new File();
//            file.setFileId(recMsg.getFileId());
//            file.setFileUniqueId(recMsg.getFileUniqueId());
//            try {
//                file.setFilePath(client.execute(GetFile.builder().fileId(file.getFileId()).build()).getFilePath());
//            } catch (TelegramApiException e) {
//                log.error(e.getMessage(), e);
//            }
//            String suffix = file.getFilePath().substring(file.getFilePath().lastIndexOf('.') + 1);
//            try (InputStream is = client.downloadFileAsStream(file);
//                 ExternalResource er = new ExternalResourceImplByByteArray(is.readAllBytes(), suffix)) {
//                Image               image   = bot.getGroup(groupId).uploadImage(er);
//                MessageChainBuilder builder = new MessageChainBuilder();
//                builder.add(image);
//                Optional.ofNullable(message.getCaption())
//                        .or(() -> Optional.ofNullable(message.getText()))
//                        .ifPresent(builder::add);
//                Optional.ofNullable(bot.getGroup(groupId)) //bot test group
//                        .ifPresent(g -> g.sendMessage(builder.build()));
//                log.debug("image: {}", ((OfflineGroupImage) image).getUrl(bot));
//            } catch (TelegramApiException | IOException e) {
//                log.error(e.getMessage(), e);
//            };
        when {
            message.hasSticker() -> {
                val sticker = message.sticker
                if (sticker.isAnimated) {
                    return true
                }
                val builder = MessageChainBuilder()
                preHandleMsg(quoteMsgSource, isMaster, username, builder)
                getImage(client, group, sticker.fileId, sticker.fileUniqueId)?.let(builder::add)

                message.caption?.let { message.text }?.let(builder::add)
                suspend {
                    val receipt: MessageReceipt<*> = group.sendMessage(builder.build())
                    cachePool.execute { CacheHolder.cache(receipt.source, message) }
                }
            }
            message.hasPhoto() -> {
                val builder = MessageChainBuilder()
                preHandleMsg(quoteMsgSource, isMaster, username, builder)
                message.photo.groupBy { it.fileId.substring(0, 40) }
                    .mapNotNull { (_: String, photoSizes: List<PhotoSize>) -> photoSizes.maxByOrNull { it.fileSize } }
                    .mapNotNull { getImage(client, group, it.fileId, it.fileUniqueId) }.forEach(builder::add)
                message.caption?.let(builder::add)
                suspend {
                    val receipt: MessageReceipt<*> = group.sendMessage(builder.build())
                    cachePool.execute { CacheHolder.cache(receipt.source, message) }
                }
            }
            message.hasText() -> {
                val builder = MessageChainBuilder()
                preHandleMsg(quoteMsgSource, isMaster, username, builder)
                builder.add(message.text)
                suspend {
                    val receipt: MessageReceipt<*> = group.sendMessage(builder.build())
                    cachePool.execute { CacheHolder.cache(receipt.source, message) }
                }
            }
        }
        return true
    }

    @Throws(Exception::class)
    override fun handleEditMessage(client: TelegramBotClient, qqClient: QQBotClient, update: Update?, message: Message): Boolean {
        suspend {
            message.messageId?.let(CacheHolder.TG_QQ_MSG_ID_CACHE::get)?.let(CacheHolder.QQ_MSG_CACHE::get)?.recall()
        }
        return handleMessage(client, qqClient, update, message)
    }

    private fun getUsername(message: Message): String {
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

    // qq 2 tg
    @Throws(Exception::class)
    override fun handle(client: QQBotClient?, telegramBotClient: TelegramBotClient, event: GroupAwareMessageEvent): Boolean {
        val group = event.group
        val groupId = group.id
        val messageChain = event.message
        val source = messageChain[OnlineMessageSource.Key]
        val replyId = messageChain[QuoteReply.Key]?.source?.ids?.get(0)?.let(CacheHolder.QQ_TG_MSG_ID_CACHE::get)
        val atAccount = AtomicLong(-100)
        var content = messageChain.filter { it !is Image }.joinToString { getContent(group, atAccount, it) }

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return true

        if (content.startsWith("\n")) {
            content = content.substring(2)
        }

        val msg = tgMsgFormat.replace(QQ_PATTNER, event.sender.id.toString())
            .replace(NAME_PATTNER, handleLongString(event.senderName, 25).replace(" @", " "))
            .replace(MSG_PATTNER, content)
        val chatId = properties.group.qqTelegram.getOrDefault(groupId, properties.group.defaultTelegram).toString()
        if (chatId.isBlank() || chatId == "0") return true
        val count = messageChain.filterIsInstance<Image>().count()
        if (count > 1) {
            val medias = messageChain.filterIsInstance<Image>()
                .map {
                    var url: String
                    runBlocking {
                        url = it.queryUrl()
                    }
                    if (it.imageId.endsWith(".gif")) {
                        InputMediaAnimation(url)
                    } else {
                        InputMediaPhoto(url)
                    }
                }
            if (medias.isNotEmpty()) {
                medias[0].caption = msg
                val builder = SendMediaGroup.builder()
                replyId?.let(builder::replyToMessageId)
                telegramBotClient.execute(
                    builder
                        .medias(medias)
                        .chatId(chatId)
                        .build()
                ).let { result ->
                    cachePool.execute {
                        source?.let { source ->
                            CacheHolder.cache(source, result[0])
                        }
                    }
                }
            }
        } else if (count == 1) {
            messageChain[Image.Key]?.let {
                var url: String
                runBlocking {
                    url = it.queryUrl()
                }
                if (it.imageId.endsWith(".git")) {
                    val builder = SendAnimation.builder()
                    replyId?.let(builder::replyToMessageId)
                    telegramBotClient.execute(
                        builder
                            .caption(msg)
                            .chatId(chatId)
                            .animation(InputFile(url))
                            .build()
                    )
                } else {
                    val builder = SendPhoto.builder()
                    replyId?.let(builder::replyToMessageId)
                    telegramBotClient.execute(builder.caption(msg).chatId(chatId).photo(InputFile(url)).build())
                }.let { m ->
                    cachePool.execute { source?.let { CacheHolder.cache(source, m) } }
                }
            }
        } else {
            val builder = SendMessage.builder()
            replyId?.let(builder::replyToMessageId)
            telegramBotClient.execute(builder.chatId(chatId).text(msg).build())
                .let { m ->
                    cachePool.execute { source?.let { CacheHolder.cache(source, m) } }
                }
        }
        log.debug("{}({}) - {}({}): {}", group.name, groupId, event.senderName, event.sender.id, content)
        return true
    }

    @Throws(TelegramApiException::class)
    override fun handleRecall(client: QQBotClient?, telegramBotClient: TelegramBotClient, event: MessageRecallEvent): Boolean {
        val message = CacheHolder.QQ_TG_MSG_ID_CACHE[event.messageIds[0]].let(CacheHolder.TG_MSG_CACHE::get)
        message?.let {
            telegramBotClient.execute(
                DeleteMessage.builder().chatId(it.chatId.toString())
                    .messageId(it.messageId)
                    .build()
            )
        }
        return true
    }

    private fun getContent(group: Group, atAccount: AtomicLong, msg: SingleMessage): String {
        return when (msg) {
            is At -> {
                return if (msg.target == atAccount.get())
                    ""
                else {
                    atAccount.set(msg.target)
                    " ${msg.getDisplay(group)} "
                }
            }
            is ForwardMessage -> {
                val sb = StringBuilder()
                sb.append("\r\n----forward message----\r\n")
                for ((_, _, senderName, messageChain) in msg.nodeList) {
                    sb.append("$senderName: ")
                    val account = AtomicLong(-100)
                    for (singleMessage in messageChain) {
                        sb.append(getContent(group, account, singleMessage))
                    }
                    sb.append("\r\n")
                }
                sb.append("-----------------------")
                sb.toString()
            }
            else -> {
                msg.contentToString()
            }
        }
    }

    override fun order(): Int {
        return 100
    }

    private fun getFile(client: TelegramBotClient, fileId: String, fileUniqueId: String): org.telegram.telegrambots.meta.api.objects.File {
        try {
            return client.execute(GetFile.builder().fileId(fileId).build())
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
        return File().apply {
            this.fileId = fileId
            this.fileUniqueId = fileUniqueId
        }
    }

    @Throws(TelegramApiException::class, IOException::class)
    private fun getImage(client: TelegramBotClient, group: Group, fileId: String, fileUniqueId: String): Image? {
        val file = getFile(client, fileId, fileUniqueId)
        val suffix = getSuffix(file)
        var image = client.downloadFile(file)
        if (suffix.equals("webp", true)) {
            val png = webp2png(file.fileId, image)
            if (png != null) image = png
        }
        var ret: Image? = null;
        try {
            image.toExternalResource().use {
                runBlocking { ret = group.uploadImage(it) }
            }
        } catch (e: IOException) {
            log.error(e.message, e)
        }
        return ret
    }

    private fun webp2png(id: String, webpFile: File): File? {
        val pngFile = File("./cache/img/$id.png")
        if (pngFile.exists()) return pngFile
        pngFile.parentFile.mkdirs()
        try {
            val future = Runtime.getRuntime().exec(String.format(webpCmdPattern, webpFile.path, pngFile.path).replace("\\", "\\\\")).onExit()
            if (future.get().exitValue() >= 0 || pngFile.exists()) return pngFile
        } catch (e: IOException) {
            log.error(e.message, e)
        } catch (e: ExecutionException) {
            log.error(e.message, e)
        } catch (e: InterruptedException) {
            log.error(e.message, e)
        }
        return null
    }

    private fun formatContent(msg: String): String {
        return msg.replace("\\n", "\r\n")
    }

    private fun handleLongString(target: String, maxLength: Int): String {
        return if (target.length > maxLength) {
            target.substring(0, maxLength) + "..."
        } else target
    }

    private fun getSuffix(file: org.telegram.telegrambots.meta.api.objects.File): String {
        return file.filePath?.let {
            it.substring(it.lastIndexOf('.').plus(1))
        } ?: ""
    }

    private fun preHandleMsg(quoteMsgSource: OnlineMessageSource?, isMaster: Boolean, username: String?, builder: MessageChainBuilder) {
        quoteMsgSource?.quote()?.let(builder::add)
        if (!isMaster && username.isNullOrBlank()) builder.add("$username: ") //非空名称或是非主人则添加前缀
    }

    companion object {
        const val QQ_PATTNER = "{qq}"
        const val NAME_PATTNER = "{name}"
        const val MSG_PATTNER = "{msg}"
        const val TG_ID_PATTNER = "{id}"
    }

    init {
        val encoderPath: String =
            if (System.getProperties().getProperty("os.name").equals("Linux", ignoreCase = true)) {
                File("./bin/dwebp").path
            } else {
                File("./bin/dwebp.exe").path
            }
        webpCmdPattern = "$encoderPath %s -o %s"
        bindingName = properties.member.bindingName
    }
}