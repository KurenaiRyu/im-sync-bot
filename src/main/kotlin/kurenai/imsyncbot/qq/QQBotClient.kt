package kurenai.imsyncbot.qq

import kotlinx.coroutines.*
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.exception.ImSyncBotRuntimeException
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.handler.qq.QQForwardHandler
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

@Component
class QQBotClient(
    properties: QQBotProperties,
    private val handlerHolder: HandlerHolder,
    private val privateChatHandler: PrivateChatHandler,
) : InitializingBean, DisposableBean {

    private val log = KotlinLogging.logger {}
    private val forwardHandler = handlerHolder.currentQQHandlerList.filterIsInstance<QQForwardHandler>()[0]
    private val isRunning = true

    private val scope = CoroutineScope(Dispatchers.Default)
    private val handlerPool = ThreadPoolExecutor(
        System.getProperty("QQ_THREAD", "10").toInt(), System.getProperty("QQ_THREAD", "10").toInt() + 10, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(System.getProperty("QQ_QUEUE", "50").toInt()), object : ThreadFactory {
            val threadNumber = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                val t = Thread(
                    Thread.currentThread().threadGroup, r,
                    "handler-thread-" + threadNumber.getAndIncrement(),
                    0
                )
                if (t.isDaemon) t.isDaemon = false
                if (t.priority != Thread.NORM_PRIORITY) t.priority = Thread.NORM_PRIORITY
                return t
            }
        }
    ) { r, e ->
        if (!e.isShutdown) {
            e.queue.poll()
            e.execute(r)
            log.warn { "handler scope queue was full, discord oldest." }
        }
    }
    private val handlerScope = handlerPool.asCoroutineDispatcher()
    val bot = BotFactory.newBot(properties.account, properties.password) {
        fileBasedDeviceInfo("./config/device.json") // 使用 device.json 存储设备信息
        protocol = properties.protocol // 切换协议
        highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
//        val file = File(BotConstant.LOG_FILE_PATH)
//        redirectBotLogToFile(file)
//        redirectNetworkLogToFile(file)
    }

    var messageCount = 0

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
                        if (GroupConfig.filterGroups.isNotEmpty() && !GroupConfig.filterGroups.contains(groupId)) {
                            false
                        } else {
                            !GroupConfig.bannedGroups.contains(groupId) && !UserConfig.bannedIds.contains(event.sender.id)
                        }.also { result ->
                            if (!result) {
                                event.message.filterIsInstance<At>()
                                    .firstOrNull { it.target == ContextHolder.masterOfQQ[0] }
                                    ?.let { handlerPool.execute { sendRemindMsg(event) } }
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
                try {
                    messageCount = messageCount % 1000 + 1
                    val c = messageCount
                    log.debug { "message-$c $event" }
                    when (event) {
                        is FriendEvent -> {
                            CoroutineScope(handlerScope).launch {
                                measureTimeMillis {
                                    privateChatHandler.onFriendEvent(event)
                                }.let {
                                    log.debug { "message-$c ${it}ms" }
                                }
                            }
                        }
                        is MessageEvent -> {
                            CoroutineScope(handlerScope).launch {
                                measureTimeMillis {
                                    handle(event)
                                }.let {
                                    log.debug { "message-$c ${it}ms" }
                                }
                            }
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
                    log.error(e) { "[message-$messageCount]Coroutine was canceled: ${e.message}" }
                } catch (e: Exception) {
                    log.error(e) { "[message-$messageCount]${e.message}" }
                }
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
        } catch (e: ImSyncBotRuntimeException) {
            log.warn { e.message }
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
                    "#提醒 #id${event.sender.id} #group${event.group.id}\n @${ContextHolder.masterUsername} $content"
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
        try {
            log.info { "Close qq bot..." }
            bot.close()
            log.info { "QQ bot closed." }
        } catch (e: Exception) {
            log.error(e) { "Close qq bot error." }
        }
    }


}