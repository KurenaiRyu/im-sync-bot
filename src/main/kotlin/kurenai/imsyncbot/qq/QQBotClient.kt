package kurenai.imsyncbot.qq

import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.exception.ImSyncBotRuntimeException
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.handler.qq.QQMessageHandler
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.BotUtil
import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.message.MessageEntity
import moe.kurenai.tdlight.model.message.User
import moe.kurenai.tdlight.request.message.SendMessage
import mu.KotlinLogging
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain.Companion.deserializeJsonToMessageChain
import net.mamoe.mirai.message.data.MessageChain.Companion.serializeToJsonString
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.md5
import net.mamoe.mirai.utils.toUHexString
import org.redisson.api.RBlockingQueue
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

@Component
class QQBotClient(
    properties: QQBotProperties,
    private val redisson: RedissonClient,
    private val handlerHolder: HandlerHolder,
    private val privateChatHandler: PrivateChatHandler,
) : InitializingBean, DisposableBean {

    private val log = KotlinLogging.logger {}
    private val messageHandle = handlerHolder.currentQQHandlerList.filterIsInstance<QQMessageHandler>()[0]
    val startCountDown = CountDownLatch(1)

    private val queueMap = HashMap<String, RBlockingQueue<String?>>()
    private val dispatcherMap = HashMap<String, ExecutorCoroutineDispatcher>()
    private val defaultScope = CoroutineScope(Dispatchers.Default)
    private val handlerPool = ThreadPoolExecutor(
        System.getProperty("QQ_THREAD", "10").toInt(),
        System.getProperty("QQ_THREAD", "10").toInt() + 10,
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(System.getProperty("QQ_QUEUE", "50").toInt()),
        object : ThreadFactory {
            val threadNumber = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                val t = Thread(
                    Thread.currentThread().threadGroup, r,
                    "qq-handler-" + threadNumber.getAndIncrement(),
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
            log.warn { "handler scope queue was full, discord oldest." }
            e.execute(r)
        }
    }
    private val handlerScope = handlerPool.asCoroutineDispatcher()
    val bot = BotFactory.newBot(properties.account, properties.password) {
        cacheDir = File("./cache/${properties.account}")
        fileBasedDeviceInfo("./config/device.json") // 使用 device.json 存储设备信息
        protocol = properties.protocol // 切换协议
        highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
//        val file = File(BotConstant.LOG_FILE_PATH)
//        redirectBotLogToFile(file)
//        redirectNetworkLogToFile(file)
    }

    var messageCount = 0
    val mapLock: Mutex = Mutex()

    @OptIn(DelicateCoroutinesApi::class)
    override fun afterPropertiesSet() {
        defaultScope.launch {
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
                                    .firstOrNull { it.target == UserConfig.masterQQ }
                                    ?.let { handlerPool.execute { sendRemindMsg(event) } }
                            }
                        }
                    }
                    is BotOfflineEvent.Dropped -> {
                        File("").inputStream().md5().toUHexString()
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
                    messageCount = (messageCount + 1) and Int.MAX_VALUE
                    val c = messageCount
                    log.debug { "message-$c $event" }

                    when (event) {
                        is MessageEvent -> {
                            val json = event.message.serializeToJsonString()
                            when (event) {
                                is FriendMessageEvent -> {
                                    val queueName = "QUEUE:FRIEND:${event.friend.id}"
                                    var queue = queueMap[queueName]
                                    if (queue == null) {
                                        mapLock.withLock {
                                            queue = queueMap[queueName] ?: let {
                                                redisson.getBlockingQueue<String?>(queueName).also { q ->
                                                    queueMap[queueName] = q
                                                    val dispatcher = dispatcherMap[queueName]
                                                        ?: newSingleThreadContext("friend#${event.friend.id}").also {
                                                            dispatcherMap[queueName] = it
                                                        }
                                                    CoroutineScope(dispatcher).launch {
                                                        val friend = event.friend
                                                        while (defaultScope.isActive) {
                                                            try {
                                                                val message =
                                                                    withContext(Dispatchers.IO) {
                                                                        q.poll(30, TimeUnit.SECONDS)
                                                                    } ?: continue
                                                                privateChatHandler.onFriendMessage(
                                                                    friend,
                                                                    message.deserializeJsonToMessageChain()
                                                                )
                                                            } catch (e: Exception) {
                                                                log.warn { e.message }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    queue!!.add(json)
                                }
                                is GroupAwareMessageEvent -> {
                                    val queueName = "QUEUE:GROUP:${event.group.id}"
                                    var queue = queueMap[queueName]
                                    if (queue == null) {
                                        mapLock.withLock {
                                            queue = queueMap[queueName] ?: let {
                                                redisson.getBlockingQueue<String?>(queueName).also { q ->
                                                    queueMap[queueName] = q
                                                    val dispatcher = dispatcherMap[queueName]
                                                        ?: newSingleThreadContext("friend#${event.group.id}").also {
                                                            dispatcherMap[queueName] = it
                                                        }
                                                    CoroutineScope(dispatcher).launch {
                                                        val group = event.group
                                                        while (defaultScope.isActive) {
                                                            try {
                                                                val message =
                                                                    withContext(Dispatchers.IO) {
                                                                        q.poll(30, TimeUnit.SECONDS)
                                                                    } ?: continue
                                                                messageHandle.onGroupMessage(
                                                                    group,
                                                                    message.deserializeJsonToMessageChain()
                                                                )
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
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    queue!!.add(json)
                                }
                                else -> {
                                    log.trace { "未支持事件 ${event.javaClass} 的处理" }
                                }
                            }
                        }
                        is MessageRecallEvent.GroupRecall -> {
                            messageHandle.onRecall(event)
                        }
                        is GroupEvent -> {
                            messageHandle.onGroupEvent(event)
                        }
                    }


//                    when (event) {
//                        is FriendEvent -> {
//                            when (event) {
//                                is FriendMessageEvent -> {
//                                }
//                            }
//
//
//                            CoroutineScope(handlerScope).launch {
//                                measureTimeMillis {
//                                    privateChatHandler.onFriendEvent(event)
//                                }.let {
//                                    log.debug { "message-$c ${it}ms" }
//                                }
//                            }
//                        }
//                        is MessageEvent -> {
//                            CoroutineScope(handlerScope).launch {
//                                measureTimeMillis {
//                                    if (DelegatingCommand.execute(event) == 0) handle(event)
//                                }.let {
//                                    log.debug { "message-$c ${it}ms" }
//                                }
//                            }
//                        }
//                        is MessageRecallEvent.GroupRecall -> {
//                            forwardHandler.onRecall(event)
//                        }
//                        is GroupEvent -> {
//                            forwardHandler.onGroupEvent(event)
//                        }
//                        else -> {
//                            log.trace { "未支持事件 ${event.javaClass} 的处理" }
//                        }
//                    }
                } catch (e: CancellationException) {
                    log.error(e) { "[message-$messageCount]Coroutine was canceled: ${e.message}" }
                } catch (e: Exception) {
                    log.error(e) { "[message-$messageCount]${e.message}" }
                }
            }
            startCountDown.countDown()
        }
    }

//    private suspend fun handle(event: Event) {
//        try {
//            for (handler in handlerHolder.currentQQHandlerList) {
//                val result = when (event) {
//                    is GroupAwareMessageEvent -> {
//                        handler.onGroupMessage(event)
//                    }
//                    else -> {
//                        CONTINUE
//                    }
//                }
//                if (result == END) break
//            }
//        } catch (e: ImSyncBotRuntimeException) {
//            log.warn { e.message }
//        } catch (e: Exception) {
//            log.error(e) { "处理信息失败，发送失败报告。" }
//            try {
//                reportError(event, e)
//            } catch (e: Exception) {
//                log.error(e) { "发送报告失败。" }
//            }
//        }
//    }

    suspend fun reportError(event: Event, e: Throwable) {
        if (event is GroupAwareMessageEvent) {
            val message = event.message
            val sender = event.sender
            val group = event.group
            val master = ContextHolder.qqBot.getFriend(UserConfig.masterQQ)
            master?.takeIf { it.id != 0L }?.sendMessage(
                master.sendMessage(message).quote()
                    .plus("group: ${group.name}(${group.id}), sender: ${sender.nameCardOrNick}(${sender.id})\n\n消息发送失败: (${e::class.simpleName}) ${e.message}")
            )
            SendMessage(BotUtil.getTgChatByQQ(event.group.id).toString(), event.message.contentToString()).send()
                .handle { _, case ->
                    case?.let {
                        log.error(case) { "Report error fail." }
                    }
                }
        }
    }

    private fun sendRemindMsg(event: GroupAwareMessageEvent) {
        if (UserConfig.masterUsername.isBlank()) return
        val content = event.message.filterIsInstance<PlainText>().map(PlainText::content).joinToString(separator = "")
        SendMessage(
            BotUtil.getTgChatByQQ(event.group.id).toString(),
            "#提醒 #id${event.sender.id} #group${event.group.id}\n $content"
        ).apply {
            entities =
                listOf(MessageEntity(MessageEntityType.TEXT_MENTION, 1, 3).apply { user = User(UserConfig.masterTg) })
        }.send().handle { _, case ->
            case?.let {
                log.error(case) { "Send remind message fail." }
            }
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