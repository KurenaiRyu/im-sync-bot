package kurenai.imsyncbot.qq

import kotlinx.coroutines.*
import kurenai.imsyncbot.BotConfigKey
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.config.BotProperties
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.handler.qq.QQForwardHandler
import kurenai.imsyncbot.service.ConfigService
import kurenai.imsyncbot.utils.BotUtil
import mu.KotlinLogging
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.util.concurrent.LinkedBlockingDeque
import kotlin.system.exitProcess

@Component
class QQBotClient(
    private val properties: QQBotProperties,
    private val botProperties: BotProperties,
    private val handlerHolder: HandlerHolder,
    private val privateChatHandler: PrivateChatHandler,
    private val configService: ConfigService
) : InitializingBean, DisposableBean {

    private val log = KotlinLogging.logger {}
    private val forwardHandler = handlerHolder.currentQQHandlerList.filterIsInstance<QQForwardHandler>()[0]
    private val eventDeque = LinkedBlockingDeque<Event>()

    private val scope = CoroutineScope(Dispatchers.Default)
    val bot = BotFactory.newBot(properties.account, properties.password) {
        fileBasedDeviceInfo() // 使用 device.json 存储设备信息
        protocol = properties.protocol // 切换协议
        highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
        val file = File(BotConstant.LOG_FILE_PATH)
        redirectBotLogToFile(file)
        redirectNetworkLogToFile(file)
    }

    override fun afterPropertiesSet() {
        scope.launch {
            log.info { "Login qq bot..." }
            bot.login()
            log.info { "Started qq-bot ${bot.nick}(${bot.id})" }
            ContextHolder.qqBot = bot
            val filter = GlobalEventChannel.filter { event ->

                return@filter when (event) {
                    is GroupAwareMessageEvent -> {
                        val groupId = event.group.id
                        if (properties.filter.group.isNotEmpty() && !properties.filter.group.contains(groupId)) {
                            false
                        } else {
                            !botProperties.ban.group.contains(groupId) && !botProperties.ban.member.contains(event.sender.id)
                        }.also {

                            val list = event.message.filterIsInstance<At>()
                            if (!it && list.isNotEmpty() && bot.id == list[0].target) {
                                sendRemindMsg(event)
                            }
                        }
                    }
                    is BotOfflineEvent.Dropped -> {
                        log.warn { "QQ bot dropped." }
                        false
                    }
                    else -> {
                        true
                    }
                }
            }

            filter.subscribeAlways<Event> { event ->
                if (eventDeque.remainingCapacity() <= 0) {
                    synchronized(eventDeque) {
                        if (eventDeque.remainingCapacity() <= 0) {
                            runBlocking { delay(500) }
                        }
                    }
                }
                try {
                    eventDeque.push(event)
                } catch (e: IllegalStateException) {
                    log.error { "QQ event queue is full, dropped. $event" }
                }
            }
            val handlerScope = CoroutineScope(Dispatchers.IO)
            handlerScope.launch {
                while (isActive) {
                    try {
                        when (val event = eventDeque.take()) {
                            is FriendEvent -> {
                                privateChatHandler.onFriendEvent(event)
                            }
                            is MessageEvent -> {
                                handle(event)
                            }
                            is MessageRecallEvent.GroupRecall -> {
                                forwardHandler.onRecall(event)
                            }
                            is GroupEvent -> {
                                forwardHandler.onGroupEvent(event)
                            }
                            else -> {
                                log.trace { "未支持事件 ${event.javaClass} 的处理" }
                            }
                        }
                    } catch (e: CancellationException) {
                        log.error(e) { "Coroutine was canceled: ${e.message}" }
                    } catch (e: Exception) {
                        log.error(e) { e.message }
                    }
                }
                log.error { "QQ handler scope is inactive." }
            }
        }
    }

    private suspend fun handle(event: Event) {
        try {
            for (handler in handlerHolder.currentQQHandlerList) {
                val result = when (event) {
                    is GroupAwareMessageEvent -> {
                        handler.onGroupMessage(event)
                    }
                    else -> {
                        CONTINUE
                    }
                }
                if (result == END) break
            }
        } catch (e: Exception) {
            log.error(e) { "处理信息失败，发送失败报告。" }
            try {
                reportError(event, e)
            } catch (e: Exception) {
                log.error(e) { "发送报告失败。" }
            }
        }
    }

    suspend fun reportError(event: Event, e: Throwable) {
        if (event is GroupAwareMessageEvent) {
            val message = event.message
            val sender = event.sender
            val group = event.group
            for (qq in ContextHolder.masterOfQQ) {
                val master = ContextHolder.qqBot.getFriend(qq)
                master?.takeIf { it.id != 0L }?.sendMessage(
                    master.sendMessage(message).quote()
                        .plus("group: ${group.name}(${group.id}), sender: ${sender.nameCardOrNick}(${sender.id})\n\n消息发送失败: ${e.message}")
                )
                ContextHolder.telegramBotClient.sendAsync(
                    SendMessage.builder().chatId(BotUtil.getTgChatByQQ(event.group.id).toString()).text(event.message.contentToString())
                        .build()
                )
            }
        }
    }

    private fun sendRemindMsg(event: GroupAwareMessageEvent) {
        try {
            val content = event.message.filterIsInstance<PlainText>().map(PlainText::content).joinToString(separator = "")
            ContextHolder.telegramBotClient.sendAsync(
                SendMessage(
                    BotUtil.getTgChatByQQ(event.group.id).toString(),
                    "#提醒 #id${event.sender.id} #group${event.group.id}\n @${configService.get(BotConfigKey.MASTER_USERNAME) ?: event.bot.nameCardOrNick} $content"
                )
            )
        } catch (e: Exception) {
            log.error(e) { "Send remind message fail." }
        }
    }

    /**
     * Invoked by the containing `BeanFactory` on destruction of a bean.
     * @throws Exception in case of shutdown errors. Exceptions will get logged
     * but not rethrown to allow other beans to release their resources as well.
     */
    override fun destroy() {
        scope.cancel()
        log.info { "Close qq-bot ${bot.nick}(${bot.id})." }
        exitProcess(0)
    }


}