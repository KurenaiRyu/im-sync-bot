package kurenai.imsyncbot.bot.qq

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.TextEntity
import it.tdlight.jni.TdApi.TextEntityTypeMentionName
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.*
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.BotUtil.toEntity
import kurenai.imsyncbot.utils.getLogger
import kurenai.imsyncbot.utils.launchWithPermit
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import top.mrxiaom.overflow.BotBuilder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class QQBot(
    private val qqProperties: QQProperties,
    private val bot: ImSyncBot
) : CoroutineScope {

    companion object {
        init {
            LocalFileService.register()
        }

        private val log = getLogger()
    }

    override val coroutineContext: CoroutineContext = bot.coroutineContext
        .plus(SupervisorJob(bot.coroutineContext[Job]))
        .plus(CoroutineExceptionHandler { context, exception ->
            when (exception) {
                is CancellationException -> {
                    log.warn("{} was cancelled", context[CoroutineName])
                }

                else -> {
                    log.warn("with {}", context[CoroutineName], exception)
                }
            }
        })

    val status = MutableStateFlow<BotStatus>(Initializing)

    private val messageChannel = Channel<Event>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST) {
        log.warn("Drop oldest event $it")
    }

    private val loginLock = Mutex()

    lateinit var qqBot: Bot

    @OptIn(DelicateCoroutinesApi::class)
    private val workerScope = CoroutineScope(coroutineContext + CoroutineName("qq-worker"))
    private val groupMessageLockMap = ConcurrentHashMap<Long, Semaphore>()

    private suspend fun buildBot(): Bot {
        val url = "ws://${qqProperties.host}:${qqProperties.port}/"
        log.info("Connecting to $url")
        return BotBuilder.positive(url)
            .token(qqProperties.token)
            .overrideLogger(log)
            .connect()?:throw BotException("Connect to $url fail!")
    }

    suspend fun start(waitForInit: Boolean = false) = loginLock.withLock {
        if (this::qqBot.isInitialized) {
            if (qqBot.isOnline) return@withLock
            if (qqBot.isActive) qqBot.close()
        }
        qqBot = buildBot()
        log.info("Login qq ${qqProperties.account}...")

        qqBot.login()
        status.update { Running }
        val initBot = suspend {
            qqBot.eventChannel.filter { event ->
                return@filter kotlin.runCatching {
                    when (event) {
                        is GroupAwareMessageEvent -> {
                            if (bot.groupConfigService.configs.isEmpty()) return@filter false
                            val groupId = event.group.id
                            if (bot.groupConfigService.filterGroups.isNotEmpty() && !bot.groupConfigService.filterGroups.contains(
                                    groupId
                                )
                            ) {
                                false
                            } else {
                                !bot.groupConfigService.bannedGroups.contains(groupId) && !bot.userConfigService.bannedIds.contains(
                                    event.sender.id
                                )
                            }.also { result ->
                                if (!result) {
                                    event.message.filterIsInstance<At>()
                                        .firstOrNull { it.target == bot.userConfigService.masterQQ }
                                        ?.let { sendRemindMsg(event) }
                                }
                            }
                        }

                        is BotOfflineEvent.Force, is BotOfflineEvent.Dropped -> {
                            status.update { Stopped }
                            log.warn("QQ bot offline.")
                            false
                        }

                        is BotReloginEvent, is BotOnlineEvent -> {
                            status.update { Running }
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
                event.id()?.let {
                    val semaphore = groupMessageLockMap.computeIfAbsent(it) { _ ->
                        Semaphore(1)
                    }
                    workerScope.launchWithPermit(semaphore, CoroutineName(event.idString())) {
                        handleEvent(event)
                    }
                } ?: run {
                    workerScope.launch(CoroutineName(event.idString())) {
                        handleEvent(event)
                    }
                }
            }

            log.info("Started qq-bot ${qqBot.nick}(${qqBot.id})")
        }
        if (waitForInit) initBot()
        else workerScope.launch { initBot() }
        handleStatus()
    }

    private fun handleStatus() {
        var previous: BotStatus? = null
        status.onEach {
            if (bot.groupConfigService.defaultTgGroup < 0) return@onEach
            when (it) {
                Initializing -> {}
                Running -> {
                    if (previous == Stopped) {
                        bot.tg.sendMessageText("#Status\nQQ bot rerunning.", bot.groupConfigService.defaultTgGroup)
                    }
                }

                Stopped -> {
                    when (previous) {
                        Running -> {
                            bot.tg.sendMessageText("#Status\nQQ bot stopped.", bot.groupConfigService.defaultTgGroup)
                        }

                        else -> {}
                    }
                }
            }
            previous = it
        }
    }

    private suspend fun handleEvent(event: Event) {
        try {
            when (event) {
                is MessageEvent -> {
                    val message = event.toEntity()
                    kotlin.runCatching {
                        MessageService.save(event.toEntity())
                    }.onFailure {
                        log.warn("Save message error", it)
                    }
                    when (event) {
                        is FriendMessageEvent -> {
//                            bot.qqMessageHandler.onFriendMessage(
//                                PrivateMessageContext(
//                                    message,
//                                    bot,
//                                    event.message,
//                                    event.friend,
//                                )
//                            )
                        }

                        is GroupTempMessageEvent -> {
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

                        is GroupAwareMessageEvent -> {
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
                    workerScope.launch {
                        bot.qqMessageHandler.onRecall(event)
                    }
                }

                is GroupEvent -> {
                    workerScope.launch {
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

    private suspend fun sendRemindMsg(event: GroupAwareMessageEvent) {
        if (bot.userConfigService.masterUsername.isBlank()) return
        val content = event.message.filterIsInstance<PlainText>().map(PlainText::content).joinToString(separator = "")
        kotlin.runCatching {
            bot.tg.sendMessageText(
                TdApi.FormattedText().apply {
                    this.text =
                        "#提醒 #id${event.sender.id} #group${event.group.id}\n${event.group.name} - ${event.sender.nameCardOrNick}:\n$content"
                    this.entities = arrayOf(TextEntity().apply {
                        this.offset = 0
                        this.length = 3
                        this.type = TextEntityTypeMentionName(bot.userConfigService.masterTg)
                    })
                },
                bot.groupConfigService.qqTg[event.group.id] ?: bot.groupConfigService.defaultTgGroup
            )
        }.onFailure {
            log.error("Send remind message fail.", it)
        }
    }

    fun close() {
        try {
            log.info("Close qq bot...")
            qqBot.close()
            log.info("QQ bot closed.")
        } catch (e: Exception) {
            log.error("Close qq bot error.", e)
        }
    }

    private fun Event.idString() = when (this) {
        is GroupEvent -> {
            "${this::class.simpleName}[${this.group.id}]"
        }

        is UserEvent -> {
            "${this::class.simpleName}[${this.user.id}]"
        }

        else -> "${this::class.simpleName}"
    }

    private fun Event.id() = when (this) {
        is GroupEvent -> {
            this.group.id
        }

        is UserEvent -> {
            this.user.id
        }

        else -> null
    }

}