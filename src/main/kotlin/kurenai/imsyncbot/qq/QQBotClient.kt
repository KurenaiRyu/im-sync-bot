package kurenai.imsyncbot.qq

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.QQProperties
import kurenai.imsyncbot.exception.ImSyncBotRuntimeException
import kurenai.imsyncbot.handler.qq.GroupMessageContext
import kurenai.imsyncbot.telegram.TelegramBot
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.message.MessageEntity
import moe.kurenai.tdlight.model.message.User
import moe.kurenai.tdlight.request.message.SendMessage
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChain.Companion.deserializeJsonToMessageChain
import net.mamoe.mirai.message.data.MessageChain.Companion.serializeToJsonString
import net.mamoe.mirai.message.data.PlainText
import org.apache.logging.log4j.LogManager
import java.io.File
import java.net.ConnectException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class QQBotClient(
    private val parentCoroutineContext: CoroutineContext,
    private val qqProperties: QQProperties,
    private val bot: ImSyncBot,
) {

    private val log = LogManager.getLogger()
    val statusChannel = Channel<QQBotStatus>(Channel.BUFFERED)
    private val messageChannel = Channel<MessageEvent>(1000, BufferOverflow.DROP_OLDEST) {
        log.warn("Drop oldest event $it")
    }
    private val scopeMap = HashMap<String, CoroutineScope>()
    var qqBot: Bot = buildMiraiBot()

    val mapLock: Mutex = Mutex()

    @OptIn(DelicateCoroutinesApi::class)
    private val workerContext = newFixedThreadPoolContext(10, "${qqProperties.account}-worker")
    private val defaultScope = CoroutineScope(parentCoroutineContext + workerContext)

    private fun buildMiraiBot(): Bot {
        return BotFactory.newBot(qqProperties.account, qqProperties.password) {
            cacheDir = File("./mirai/${qqProperties.account}")
            fileBasedDeviceInfo("${bot.configPath}/device.json") // 使用 device.json 存储设备信息
            protocol = qqProperties.protocol // 切换协议
            highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
            parentCoroutineContext = this@QQBotClient.parentCoroutineContext
//        val file = File(BotConstant.LOG_FILE_PATH)
//        redirectBotLogToFile(file)
//        redirectNetworkLogToFile(file)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun start() {
        log.info("Login qq ${qqProperties.account}...")
        qqBot.login()
        defaultScope.launch {
            // TODO: 获取之前已存在的queue
            // bot.redisson.keys
            statusChannel.send(Initialized)
            var telegramBotStatus = bot.tg.statusChannel.receive()
            while (telegramBotStatus !is TelegramBot.Initialized) {
                log.debug("Telegram bot status: ${telegramBotStatus.javaClass.simpleName}")
                telegramBotStatus = bot.tg.statusChannel.receive()
            }
            qqBot.eventChannel.filter { event ->
                return@filter kotlin.runCatching {
                    when (event) {
                        is GroupAwareMessageEvent -> {
                            if (bot.groupConfig.items.isEmpty()) return@filter false
                            val groupId = event.group.id
                            if (bot.groupConfig.filterGroups.isNotEmpty() && !bot.groupConfig.filterGroups.contains(groupId)) {
                                false
                            } else {
                                !bot.groupConfig.bannedGroups.contains(groupId) && !bot.userConfig.bannedIds.contains(event.sender.id)
                            }.also { result ->
                                if (!result) {
                                    event.message.filterIsInstance<At>().firstOrNull { it.target == bot.userConfig.masterQQ }
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
            }.subscribeAlways<Event> { event ->
                try {
                    when (event) {
                        is MessageEvent -> {
                            val json = event.message.serializeToJsonString()
                            when (event) {
                                is FriendMessageEvent -> {
                                    val friend = event.friend
                                    val id = friend.id
                                    val queueName = "QUEUE:FRIEND:$id"
                                    val queue = bot.redisson.getBlockingQueue<String?>(queueName)
                                    val scope = scopeMap[queueName]
                                    if (scope == null) {
                                        mapLock.withLock {
                                            scopeMap.getOrPut(queueName) {
                                                CoroutineScope(
                                                    bot.coroutineContext +
                                                            workerContext.limitedParallelism(1) +
                                                            CoroutineName("${friend.nameCardOrNick}(${friend.id})")
                                                )
                                            }.launch {
                                                while (isActive) {
                                                    try {
                                                        val message =
                                                            queue.pollAsync(10, TimeUnit.MINUTES)
                                                                .toCompletableFuture()
                                                                .await()
                                                        if (message != null) {
                                                            bot.privateHandle.onFriendMessage(
                                                                friend,
                                                                message.deserializeJsonToMessageChain(),
                                                                friend.id == qqBot.id
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        log.error("Handle friend message fail", e)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    queue.addAsync(json)
                                }

                                is GroupAwareMessageEvent -> {
                                    val id = event.group.id
                                    val queueName = "QUEUE:GROUP:$id"
                                    val queue = bot.redisson.getBlockingQueue<String?>(queueName)
                                    val scope = scopeMap[queueName]
                                    if (scope == null) {
                                        mapLock.withLock {
                                            scopeMap.getOrPut(queueName) {
                                                CoroutineScope(
                                                    bot.coroutineContext +
                                                            workerContext.limitedParallelism(1) +
                                                            CoroutineName("${event.group.name}(${event.group.id})")
                                                )
                                            }.launch {
                                                val group = event.group
                                                while (isActive) {
                                                    var messageChain: MessageChain? = null
                                                    try {
                                                        val message =
                                                            queue.pollAsync(30, TimeUnit.SECONDS).toCompletableFuture()
                                                                .await() ?: continue
                                                        messageChain = message.deserializeJsonToMessageChain()
                                                        var count = 0
                                                        while (count < 3) {
                                                            try {
                                                                bot.qqMessageHandler.onGroupMessage(GroupMessageContext(bot, group, messageChain))
                                                                break
                                                            } catch (e: ConnectException) {
                                                                log.warn(e.message)
                                                                count++
                                                                delay(count * 2000L)
                                                            }
                                                        }
                                                    } catch (e: ImSyncBotRuntimeException) {
                                                        log.warn(e.message)
                                                    } catch (e: Exception) {
                                                        reportError(group, messageChain!!, e)
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
                            event.messageIds
                            defaultScope.launch {
                                bot.qqMessageHandler.onRecall(event)
                            }
                        }

                        is GroupEvent -> {
                            defaultScope.launch {
                                bot.qqMessageHandler.onGroupEvent(event)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    log.error("Coroutine was canceled: ${e.message}\n$event", e)
                } catch (e: Exception) {
                    log.error("Subscribe error: ${e.message}\n$event", e)
                }
            }
            log.info("Started qq-bot ${qqBot.nick}(${qqBot.id})")
        }
    }

    suspend fun restart() {
        kotlin.runCatching {
            qqBot.login()
        }.recoverCatching { ex ->
            log.error("Re login failed, try to create a new instance...")
            qqBot = buildMiraiBot()
            start()
        }.getOrThrow()
    }

    suspend fun reportError(group: Group, messageChain: MessageChain, throwable: Throwable) {
        log.error(throwable.message, throwable)
        try {
//            val senderId = messageChain.source.fromId
//            val master = bot.getFriend(imSyncBot.userConfig.masterQQ)
//            master?.takeIf { it.id != 0L }?.sendMessage(
//                master.sendMessage(messageChain).quote()
//                    .plus("group: ${group.name}(${group.id}), sender: ${group[senderId]?.remarkOrNameCardOrNick}(${senderId})\n\n消息发送失败: (${throwable::class.simpleName}) ${throwable.message}")
//            )
            kotlin.runCatching {
                SendMessage(
                    (bot.groupConfig.qqTg[group.id] ?: bot.groupConfig.defaultTgGroup).toString(),
                    messageChain.contentToString()
                ).send()
            }.onFailure {
                log.error("Report error fail.", it)
            }.getOrNull()
        } catch (e: Exception) {
            log.error("Report error fail: ${e.message}", e)
        }
    }

    private suspend fun sendRemindMsg(event: GroupAwareMessageEvent) {
        if (bot.userConfig.masterUsername.isBlank()) return
        val content = event.message.filterIsInstance<PlainText>().map(PlainText::content).joinToString(separator = "")
        kotlin.runCatching {
            SendMessage(
                (bot.groupConfig.qqTg[event.group.id] ?: bot.groupConfig.defaultTgGroup).toString(),
                "#提醒 #id${event.sender.id} #group${event.group.id}\n $content"
            ).apply {
                entities =
                    listOf(MessageEntity(MessageEntityType.TEXT_MENTION, 1, 3).apply {
                        user = User(bot.userConfig.masterTg)
                    })
            }.send()
        }.onFailure {
            log.error("Send remind message fail.", it)
        }
    }

    fun destroy() {
        try {
            log.info("Close qq bot...")
            qqBot.close()
            log.info("QQ bot closed.")
        } catch (e: Exception) {
            log.error("Close qq bot error.", e)
        }
    }

    sealed interface QQBotStatus

    object Initializing : QQBotStatus
    object Initialized : QQBotStatus


}