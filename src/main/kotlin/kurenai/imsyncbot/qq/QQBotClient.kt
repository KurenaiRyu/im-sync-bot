package kurenai.imsyncbot.qq

import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.configProperties
import kurenai.imsyncbot.exception.ImSyncBotRuntimeException
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.qqMessageHandler
import kurenai.imsyncbot.redisson
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.BotUtil
import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.message.MessageEntity
import moe.kurenai.tdlight.model.message.User
import moe.kurenai.tdlight.request.message.SendMessage
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain.Companion.deserializeJsonToMessageChain
import net.mamoe.mirai.message.data.MessageChain.Companion.serializeToJsonString
import net.mamoe.mirai.message.data.PlainText
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object QQBotClient {

    private val log = LogManager.getLogger()
    val startCountDown = CountDownLatch(1)

    private val scopeMap = HashMap<Long, CoroutineScope>()
    val bot = BotFactory.newBot(configProperties.bot.qq.account, configProperties.bot.qq.password) {
        cacheDir = File("./mirai/${configProperties.bot.qq.account}")
        fileBasedDeviceInfo("./config/device.json") // 使用 device.json 存储设备信息
        protocol = configProperties.bot.qq.protocol // 切换协议
        highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
//        val file = File(BotConstant.LOG_FILE_PATH)
//        redirectBotLogToFile(file)
//        redirectNetworkLogToFile(file)
    }

    var messageCount = 0
    val mapLock: Mutex = Mutex()

    suspend fun start() = runBlocking {
        log.info("Login qq bot...")
        bot.login()
        log.info("Started qq-bot ${bot.nick}(${bot.id})")
        val filter = GlobalEventChannel.filter { event ->
            return@filter kotlin.runCatching {
                when (event) {
                    is GroupAwareMessageEvent -> {
                        val groupId = event.group.id
                        if (GroupConfig.filterGroups.isNotEmpty() && !GroupConfig.filterGroups.contains(groupId)) {
                            false
                        } else {
                            !GroupConfig.bannedGroups.contains(groupId) && !UserConfig.bannedIds.contains(event.sender.id)
                        }.also { result ->
                            if (!result) {
                                event.message.filterIsInstance<At>().firstOrNull { it.target == UserConfig.masterQQ }
                                    ?.let { sendRemindMsg(event) }
                            }
                        }
                    }

                    is BotOfflineEvent.Dropped -> {
                        log.warn("QQ bot dropped.")
                        false
                    }

                    else -> {
                        true
                    }
                }
            }.onFailure {
                log.error(it.message, it)
            }.getOrDefault(false)
        }

        filter.subscribeAlways<Event> { event ->
            try {
                messageCount = (messageCount + 1) and Int.MAX_VALUE
                val c = messageCount
                log.debug("message-$c $event")

                when (event) {
                    is MessageEvent -> {
                        val json = event.message.serializeToJsonString()
                        when (event) {
                            is FriendMessageEvent -> {
                                val id = event.friend.id
                                val queueName = "QUEUE:FRIEND:$id"
                                val queue = redisson.getBlockingQueue<String?>(queueName)
                                var scope = scopeMap[id]
                                if (scope == null) {
                                    mapLock.withLock {
                                        scope = scopeMap[id]
                                        if (scope == null) {
                                            scopeMap[id] =
                                                CoroutineScope(newSingleThreadContext("${event.friend.nameCardOrNick}(${event.friend.id})"))
                                        }
                                        scope = scopeMap[id]
                                        scope!!.launch {
                                            val friend = event.friend
                                            while (isActive) {
                                                try {
                                                    val message =
                                                        queue.pollAsync(10, TimeUnit.MINUTES)
                                                            .toCompletableFuture()
                                                            .await()
                                                    if (message != null) {
                                                        PrivateChatHandler.onFriendMessage(
                                                            friend,
                                                            message.deserializeJsonToMessageChain()
                                                        )
                                                    } else {
                                                        mapLock.withLock {
                                                            scopeMap.remove(friend.id)
                                                        }
                                                        this.cancel()
                                                    }
                                                } catch (e: Exception) {
                                                    reportError(event, e)
                                                }
                                            }
                                        }
                                    }
                                }
                                queue.add(json)
                            }
                            is GroupAwareMessageEvent -> {
                                val id = event.group.id
                                val queueName = "QUEUE:GROUP:$id"
                                val queue = redisson.getBlockingQueue<String?>(queueName)
                                var scope = scopeMap[id]
                                if (scope == null) {
                                    mapLock.withLock {
                                        scope = scopeMap[id]
                                        if (scope == null) {
                                            scopeMap[id] =
                                                CoroutineScope(newSingleThreadContext("${event.group.name}(${event.group.id})"))
                                        }
                                        scope = scopeMap[id]
                                        scope!!.launch {
                                            val group = event.group
                                            while (isActive) {
                                                try {
                                                    val message =
                                                        queue.pollAsync(30, TimeUnit.SECONDS).toCompletableFuture()
                                                            .await() ?: continue
                                                    qqMessageHandler.onGroupMessage(
                                                        group,
                                                        message.deserializeJsonToMessageChain()
                                                    )
                                                } catch (e: ImSyncBotRuntimeException) {
                                                    log.warn(e.message)
                                                } catch (e: Exception) {
                                                    reportError(event, e)
                                                }
                                            }
                                        }
                                    }
                                }
                                queue.add(json)
                            }
                            else -> {
                                log.trace("未支持事件 ${event.javaClass} 的处理")
                            }
                        }
                    }
                    is MessageRecallEvent.GroupRecall -> {
                        qqMessageHandler.onRecall(event)
                    }
                    is GroupEvent -> {
                        qqMessageHandler.onGroupEvent(event)
                    }
                }

            } catch (e: CancellationException) {
                log.error("[message-$messageCount]Coroutine was canceled: ${e.message}", e)
            } catch (e: Exception) {
                log.error("[message-$messageCount]${e.message}", e)
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread {
            destroy()
        })
        startCountDown.countDown()
    }

    suspend fun reportError(event: Event, throwable: Throwable) {
        log.error(throwable.message, throwable)
        try {
            if (event is GroupAwareMessageEvent) {
                val message = event.message
                val sender = event.sender
                val group = event.group
                val master = bot.getFriend(UserConfig.masterQQ)
                master?.takeIf { it.id != 0L }?.sendMessage(
                    master.sendMessage(message).quote()
                        .plus("group: ${group.name}(${group.id}), sender: ${sender.nameCardOrNick}(${sender.id})\n\n消息发送失败: (${throwable::class.simpleName}) ${throwable.message}")
                )
                kotlin.runCatching {
                    SendMessage(
                        BotUtil.getTgChatByQQ(event.group.id).toString(),
                        event.message.contentToString()
                    ).send()
                }.onFailure {
                    log.error("Report error fail.", it)
                }.getOrNull()
            }
        } catch (e: Exception) {
            log.error("Report error fail: ${e.message}", e)
        }
    }

    private suspend fun sendRemindMsg(event: GroupAwareMessageEvent) {
        if (UserConfig.masterUsername.isBlank()) return
        val content = event.message.filterIsInstance<PlainText>().map(PlainText::content).joinToString(separator = "")
        kotlin.runCatching {
            SendMessage(
                BotUtil.getTgChatByQQ(event.group.id).toString(),
                "#提醒 #id${event.sender.id} #group${event.group.id}\n $content"
            ).apply {
                entities =
                    listOf(MessageEntity(MessageEntityType.TEXT_MENTION, 1, 3).apply {
                        user = User(UserConfig.masterTg)
                    })
            }.send()
        }.onFailure {
            log.error("Send remind message fail.", it)
        }
    }

    fun destroy() {
        try {
            log.info("Close qq bot...")
            bot.close()
            log.info("QQ bot closed.")
        } catch (e: Exception) {
            log.error("Close qq bot error.", e)
        }
    }


}