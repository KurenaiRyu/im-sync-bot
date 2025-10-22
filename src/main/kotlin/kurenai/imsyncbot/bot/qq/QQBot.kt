package kurenai.imsyncbot.bot.qq

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.TextEntity
import it.tdlight.jni.TdApi.TextEntityTypeMentionName
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.*
import kurenai.imsyncbot.bot.MessageDispatcher
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.service.FriendConfigService
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.BotUtil.toEntity
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import top.mrxiaom.overflow.BotBuilder
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


class QQBot(
    private val botProperties: BotProperties,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) {

    companion object {
        init {
            LocalFileService.register()
        }

        private val log = getLogger()
    }

    private val qqScope = CoroutineScope(
        SupervisorJob(coroutineContext[Job]) +
                Dispatchers.Default +
                CoroutineExceptionHandler { context, exception ->
                    when (exception) {
                        is CancellationException -> {
                            log.warn("{} was cancelled", context[CoroutineName])
                        }

                        else -> {
                            log.warn("with {}", context[CoroutineName], exception)
                        }
                    }
                }
    )
    internal var messageHandler: QQMessageHandler = QQMessageHandler(botProperties, qqScope)

    val status = MutableStateFlow<BotStatus>(Initializing)

    private val loginLock = Mutex()

    lateinit var qqBot: Bot
    private val messageDispatcher = MessageDispatcher(parentScope = qqScope, name = "qq-group-message-dispatcher")

    private suspend fun buildBot(): Bot {
        val url = "ws://${botProperties.qq.host}:${botProperties.qq.port}/"
        log.info("Connecting to $url")
        return BotBuilder.positive(url)
            .token(botProperties.qq.token)
            .overrideLogger(log)
            .connect() ?: throw BotException("Connect to $url fail!")
    }

    context(bot: ImSyncBot)
    suspend fun start(waitForInit: Boolean = false) = loginLock.withLock {
        if (this::qqBot.isInitialized) {
            if (qqBot.isOnline) return@withLock
            if (qqBot.isActive) qqBot.close()
        }
        qqBot = buildBot()
        log.info("Login qq ${botProperties.qq.account}...")

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
                event.idWithGroupOrUserMark()?.also {
                    messageDispatcher.submit(it) {
                        handleEvent(event)
                    }
                } ?: run {
                    qqScope.launch {
                        handleEvent(event)
                    }
                }
            }

            log.info("Started qq-bot ${qqBot.nick}(${qqBot.id})")
        }
        val job = qqScope.launch {
            initBot()
        }

        if (waitForInit) job.join()
        qqScope.launch {
            handleStatus()
        }
    }

    context(bot: ImSyncBot)
    private suspend fun handleStatus() {
        var previous: BotStatus? = null
        status.collect {
            if (bot.groupConfigService.defaultTgGroup < 0) return@collect
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

    context(bot: ImSyncBot)
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
                            messageHandler.onFriendMessage(
                                PrivateMessageContext(
                                    message,
                                    qqScope,
                                    bot.configProperties.bot,
                                    event,
                                )
                            )
                        }

                        is GroupTempMessageEvent -> {
                            messageHandler.onGroupMessage(
                                GroupMessageContext(
                                    message,
                                    qqScope,
                                    bot,
                                    event,
                                    event.group,
                                    event.message
                                )
                            )
                        }

                        is GroupAwareMessageEvent -> {
                            messageHandler.onGroupMessage(
                                GroupMessageContext(
                                    message,
                                    qqScope,
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
                    messageHandler.onRecall(event)
                }

                is GroupEvent -> {
                    qqScope.launch {
                        messageHandler.onGroupEvent(event)
                    }
                }
            }
        } catch (e: CancellationException) {
            log.error("Coroutine was canceled: ${e.message}\n$event", e)
        } catch (e: Exception) {
            log.error("Subscribe error: ${e.message}\n$event", e)
        }
    }

    context(bot: ImSyncBot)
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
                bot.groupConfigService.findByQQ(event.group.id)?.telegramGroupId
                    ?: bot.groupConfigService.defaultTgGroup
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

    private fun Event.idWithClsName() = when (this) {
        is GroupEvent -> {
            "${this::class.simpleName}[${this.group.id}]"
        }

        is UserEvent -> {
            "${this::class.simpleName}[${this.user.id}]"
        }

        else -> "${this::class.simpleName}"
    }

    private fun Event.idWithGroupOrUserMark() = when (this) {
        is GroupEvent -> {
            "G${this.group.id}"
        }

        is UserEvent -> {
            "U[${this.user.id}]"
        }

        else -> null
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