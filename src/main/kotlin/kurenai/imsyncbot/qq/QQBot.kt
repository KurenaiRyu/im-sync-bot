package kurenai.imsyncbot.qq

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.QQProperties
import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.domain.getLocalDateTime
import kurenai.imsyncbot.qq.login.MultipleLoginSolver
import kurenai.imsyncbot.qqMessageRepository
import kurenai.imsyncbot.telegram.TelegramBot
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.message.MessageEntity
import moe.kurenai.tdlight.model.message.User
import moe.kurenai.tdlight.request.message.SendMessage
import moe.kurenai.tdlight.util.getLogger
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.auth.BotAuthorization
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChain.Companion.serializeToJsonString
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.ids
import net.mamoe.mirai.utils.BotConfiguration
import java.io.File
import kotlin.coroutines.CoroutineContext

class QQBot(
    private val parentCoroutineContext: CoroutineContext,
    private val qqProperties: QQProperties,
    private val bot: ImSyncBot,
) {
    private val log = getLogger()
    val status: AtomicRef<QQBotStatus> = atomic(Initializing)

    private val messageChannel = Channel<Event>(200, BufferOverflow.SUSPEND) {
        log.warn("Drop oldest event $it")
    }

    private val loginLock = Mutex()
    lateinit var qqBot: Bot

    val jobLock: Mutex = Mutex()

    @OptIn(DelicateCoroutinesApi::class)
    private val workerContext = newFixedThreadPoolContext(10, "${qqProperties.account}-worker")
    private val defaultScope = CoroutineScope(parentCoroutineContext + workerContext)

    private fun buildMiraiBot(qrCodeLogin: Boolean = false): Bot {
//        log.info("协议版本检查更新...")
//        try {
//            FixProtocolVersion.update()
//        } catch (cause: Throwable) {
//            log.error("协议版本升级失败", cause)
//        }
        val configuration = BotConfiguration().apply {
            cacheDir = File("./mirai/${qqProperties.account}")
            fileBasedDeviceInfo("${bot.configPath}/device.json") // 使用 device.json 存储设备信息
            protocol = qqProperties.protocol // 切换协议
            highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
            parentCoroutineContext = this@QQBot.parentCoroutineContext
            loginSolver = MultipleLoginSolver(bot)
        }
        return if (qrCodeLogin || qqProperties.password.isBlank()) {
            BotFactory.newBot(qqProperties.account, BotAuthorization.byQRCode(), configuration)
        } else {
            BotFactory.newBot(qqProperties.account, qqProperties.password, configuration)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun start(waitForInit: Boolean = false) = loginLock.withLock {
        if (this::qqBot.isInitialized) {
            if (qqBot.isOnline) return@withLock
            if (qqBot.isActive) qqBot.close()
        }
        qqBot = buildMiraiBot()
        log.info("Login qq ${qqProperties.account}...")

        qqBot.login()
        status.update { Initialized }
        val initBot = suspend {
            // TODO: 获取之前已存在的queue
            // bot.redisson.keys
            var telegramBotStatus = bot.tg.status.value
            var count = 0
            while (telegramBotStatus !is TelegramBot.Initialized) {
                log.debug("Telegram bot status: ${telegramBotStatus.javaClass.simpleName}")
                delay((1000 + count * 1000L).coerceAtMost(10000))
                telegramBotStatus = bot.tg.status.value
                count++
            }
            qqBot.eventChannel.filter { event ->
                return@filter kotlin.runCatching {
                    when (event) {
                        is GroupAwareMessageEvent -> {
                            if (bot.groupConfig.items.isEmpty()) return@filter false
                            val groupId = event.group.id
                            if (bot.groupConfig.filterGroups.isNotEmpty() && !bot.groupConfig.filterGroups.contains(
                                    groupId
                                )
                            ) {
                                false
                            } else {
                                !bot.groupConfig.bannedGroups.contains(groupId) && !bot.userConfig.bannedIds.contains(
                                    event.sender.id
                                )
                            }.also { result ->
                                if (!result) {
                                    event.message.filterIsInstance<At>()
                                        .firstOrNull { it.target == bot.userConfig.masterQQ }
                                        ?.let { sendRemindMsg(event) }
                                }
                            }
                        }

                        is BotOfflineEvent.Force, is BotOfflineEvent.Dropped -> {
                            status.update { Offline }
                            log.warn("QQ bot offline.")
                            false
                        }

                        is BotReloginEvent, is BotOnlineEvent -> {
                            status.update { Online }
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
                messageChannel.send(event)
            }

            messageChannel.receiveAsFlow().collect {
                handleEvent(it)
            }

            log.info("Started qq-bot ${qqBot.nick}(${qqBot.id})")
        }
        if (waitForInit) initBot.invoke()
        else defaultScope.launch { initBot.invoke() }
    }

    suspend fun handleEvent(event: Event) {
        try {
            when (event) {
                is MessageEvent -> {
                    val json = event.message.serializeToJsonString()
                    when (event) {
                        is FriendMessageEvent -> {
                            val message = qqMessageRepository.save(
                                QQMessage(
                                    event.message.ids[0],
                                    event.bot.id,
                                    event.subject.id,
                                    event.sender.id,
                                    event.source.targetId,
                                    QQMessage.QQMessageType.FRIEND,
                                    json,
                                    false,
                                    event.source.getLocalDateTime()
                                )
                            )

                            bot.qqMessageHandler.onFriendMessage(
                                PrivateMessageContext(
                                    message,
                                    bot,
                                    event.message,
                                    event.friend,
                                )
                            )
                        }

                        is GroupAwareMessageEvent -> {
                            val message = qqMessageRepository.save(
                                QQMessage(
                                    event.message.ids[0],
                                    event.bot.id,
                                    event.subject.id,
                                    event.sender.id,
                                    event.source.targetId,
                                    if (event is GroupTempMessageEvent) QQMessage.QQMessageType.GROUP_TEMP else QQMessage.QQMessageType.GROUP,
                                    json,
                                    false,
                                    event.source.getLocalDateTime()
                                )
                            )

                            bot.qqMessageHandler.onGroupMessage(
                                GroupMessageContext(
                                    message,
                                    bot,
                                    event,
                                    event.group,
                                    event.message
                                )
                            )
                        }

                        else -> {
                            log.trace("未支持事件 {} 的处理", event.javaClass)
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

    suspend fun restart() {
        kotlin.runCatching {
            qqBot.login()
        }.recoverCatching { ex ->
            log.error("Re login failed, try to create a new instance...", ex)
            start(true)
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
    object Offline : QQBotStatus
    object Online : QQBotStatus


}