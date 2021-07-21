//package kurenai.mybot.handler.impl
//
//import net.mamoe.mirai.event.events.MessageEvent.message
//import net.mamoe.mirai.message.code.CodableMessage.serializeToMiraiCode
//import net.mamoe.mirai.event.events.MessageEvent.subject
//import net.mamoe.mirai.contact.Contact.id
//import net.mamoe.mirai.Bot.getGroup
//import net.mamoe.mirai.message.data.MessageChainBuilder.build
//import net.mamoe.mirai.message.MessageReceipt.source
//import net.mamoe.mirai.message.data.MessageChainBuilder.addAll
//import net.mamoe.mirai.message.data.MessageChainBuilder.add
//import net.mamoe.mirai.event.events.GroupAwareMessageEvent.group
//import net.mamoe.mirai.contact.Group.id
//import net.mamoe.mirai.message.data.MessageChain.get
//import net.mamoe.mirai.event.events.MessageEvent.sender
//import net.mamoe.mirai.contact.User.id
//import net.mamoe.mirai.event.events.MessageEvent.senderName
//import net.mamoe.mirai.internal.message.OnlineImage.originUrl
//import net.mamoe.mirai.message.data.Image.imageId
//import net.mamoe.mirai.contact.Group.name
//import net.mamoe.mirai.event.events.MessageRecallEvent.messageIds
//import net.mamoe.mirai.message.data.At.target
//import net.mamoe.mirai.message.data.At.getDisplay
//import net.mamoe.mirai.message.data.ForwardMessage.nodeList
//import net.mamoe.mirai.message.data.ForwardMessage.Node.senderName
//import net.mamoe.mirai.message.data.ForwardMessage.Node.messageChain
//import net.mamoe.mirai.message.data.Message.contentToString
//import net.mamoe.mirai.message.data.MessageSource.ids
//import net.mamoe.mirai.BotFactory.INSTANCE.newBot
//import net.mamoe.mirai.utils.BotConfiguration.fileBasedDeviceInfo
//import net.mamoe.mirai.utils.BotConfiguration.protocol
//import net.mamoe.mirai.Bot.bot
//import net.mamoe.mirai.Bot.nick
//import net.mamoe.mirai.Bot.id
//import net.mamoe.mirai.Bot.eventChannel
//import net.mamoe.mirai.event.EventChannel.filter
//import net.mamoe.mirai.event.events.GroupMessageEvent.group
//import net.mamoe.mirai.event.events.GroupMessageSyncEvent.group
//import net.mamoe.mirai.event.EventChannel.subscribeAlways
//import org.springframework.boot.context.properties.ConfigurationProperties
//import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
//import kotlin.jvm.JvmOverloads
//import java.util.concurrent.ConcurrentHashMap
//import kurenai.mybot.cache.DelayItem
//import java.lang.InterruptedException
//import java.time.LocalDateTime
//import java.lang.Runnable
//import java.util.concurrent.Delayed
//import java.time.ZoneId
//import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
//import org.apache.http.client.config.RequestConfig
//import kotlin.Throws
//import java.security.NoSuchAlgorithmException
//import java.security.KeyStoreException
//import java.security.KeyManagementException
//import java.io.IOException
//import org.apache.http.impl.client.CloseableHttpClient
//import kurenai.mybot.utils.HttpUtil
//import org.apache.http.client.methods.CloseableHttpResponse
//import org.apache.http.HttpEntity
//import org.apache.http.util.EntityUtils
//import org.apache.http.conn.ssl.SSLConnectionSocketFactory
//import javax.net.ssl.SSLSession
//import org.apache.http.impl.client.HttpClients
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
//import lombok.extern.slf4j.Slf4j
//import kurenai.mybot.TelegramBotClient
//import kurenai.mybot.QQBotClient
//import org.telegram.telegrambots.meta.api.objects.Update
//import org.telegram.telegrambots.meta.api.methods.send.SendMessage
//import org.telegram.telegrambots.meta.exceptions.TelegramApiException
//import kurenai.mybot.handler.impl.EchoHandler
//import org.springframework.boot.context.properties.EnableConfigurationProperties
//import kurenai.mybot.handler.config.ForwardHandlerProperties
//import net.mamoe.mirai.event.events.GroupAwareMessageEvent
//import kurenai.mybot.handler.impl.PixivHandler
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//import net.mamoe.mirai.Bot
//import net.mamoe.mirai.message.data.OnlineMessageSource
//import kurenai.mybot.CacheHolder
//import kurenai.mybot.handler.impl.ForwardHandler
//import org.telegram.telegrambots.meta.api.objects.stickers.Sticker
//import net.mamoe.mirai.message.data.MessageChainBuilder
//import net.mamoe.mirai.message.MessageReceipt
//import org.telegram.telegrambots.meta.api.objects.PhotoSize
//import java.util.stream.Collectors
//import io.ktor.util.collections.ConcurrentList
//import java.util.concurrent.ExecutionException
//import net.mamoe.mirai.message.data.MessageChain
//import net.mamoe.mirai.message.data.QuoteReply
//import net.mamoe.mirai.message.data.SingleMessage
//import net.mamoe.mirai.internal.message.OnlineGroupImage
//import org.telegram.telegrambots.meta.api.objects.media.InputMedia
//import org.telegram.telegrambots.meta.api.objects.media.InputMediaAnimation
//import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
//import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup.SendMediaGroupBuilder
//import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
//import java.util.function.BiFunction
//import org.telegram.telegrambots.meta.api.methods.send.SendAnimation.SendAnimationBuilder
//import org.telegram.telegrambots.meta.api.methods.send.SendAnimation
//import org.telegram.telegrambots.meta.api.objects.InputFile
//import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
//import org.telegram.telegrambots.meta.api.methods.send.SendMessage.SendMessageBuilder
//import net.mamoe.mirai.event.events.MessageRecallEvent
//import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
//import net.mamoe.mirai.message.data.At
//import java.lang.StringBuilder
//import org.telegram.telegrambots.meta.api.methods.GetFile
//import net.mamoe.mirai.utils.ExternalResource
//import java.util.concurrent.CompletableFuture
//import java.lang.Process
//import java.net.URLEncoder
//import kurenai.mybot.handler.impl.PicSourceHandler
//import kurenai.mybot.handler.config.AntiMiniAppHandlerProperties
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.dataformat.xml.XmlMapper
//import kurenai.mybot.handler.impl.AntiMiniAppHandler
//import com.fasterxml.jackson.core.JsonProcessingException
//import kurenai.mybot.handler.HandlerProperties
//import java.util.HashMap
//import org.telegram.telegrambots.bots.DefaultBotOptions
//import kurenai.mybot.qq.QQBotProperties
//import kurenai.mybot.HandlerHolder
//import kurenai.mybot.handler.Handler
//import org.springframework.context.ApplicationContext
//import net.mamoe.mirai.event.EventChannel
//import net.mamoe.mirai.event.events.BotEvent
//import net.mamoe.mirai.event.events.GroupMessageEvent
//import net.mamoe.mirai.event.events.GroupMessageSyncEvent
//import net.mamoe.mirai.utils.BotConfiguration
//import org.springframework.boot.autoconfigure.SpringBootApplication
//import kotlin.jvm.JvmStatic
//import org.springframework.boot.SpringApplication
//import kurenai.mybot.telegram.TelegramBotProperties
//import org.telegram.telegrambots.bots.TelegramLongPollingBot
//import kurenai.mybot.telegram.ProxyProperties
//import org.springframework.stereotype.Component
//import org.telegram.telegrambots.meta.api.objects.Message
//import java.lang.Exception
//
//@Component
//@ConditionalOnProperty(prefix = "bot.handler.echo", name = ["enable"], havingValue = "true")
//@Slf4j
//class EchoHandler : Handler {
//    @Throws(Exception::class)
//    override fun handleMessage(client: TelegramBotClient, qqBotClient: QQBotClient, update: Update?, message: Message): Boolean {
//        if (message.hasText()) {
//            val sendMessage = SendMessage() // Create a SendMessage object with mandatory fields
//            sendMessage.chatId = message.chatId.toString()
//            sendMessage.text = message.text
//            sendMessage.replyToMessageId = message.messageId
//            try {
//                client.execute(sendMessage)
//            } catch (e: TelegramApiException) {
//                EchoHandler.log.error(e.message, e)
//            }
//            return true
//        }
//        return true
//    }
//
//    override fun order(): Int {
//        return 0
//    }
//}