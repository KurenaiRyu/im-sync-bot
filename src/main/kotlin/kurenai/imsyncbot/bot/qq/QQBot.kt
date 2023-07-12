package kurenai.imsyncbot.bot.qq

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.TextEntity
import it.tdlight.jni.TdApi.TextEntityTypeMentionName
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.*
import kurenai.imsyncbot.bot.qq.login.qsign.UnidbgFetchQSignFactory
import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.domain.getLocalDateTime
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.utils.FixProtocolVersion
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.auth.BotAuthorization
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.internal.spi.EncryptService
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain.Companion.serializeToJsonString
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.ids
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.Services
import java.io.File
import kotlin.coroutines.CoroutineContext

class QQBot(
    private val parentCoroutineContext: CoroutineContext,
    private val qqProperties: QQProperties,
    private val bot: ImSyncBot,
) {
    private val log = getLogger()
    val status = MutableStateFlow<BotStatus>(Initializing)

    private val messageChannel = Channel<Event>(Channel.BUFFERED, BufferOverflow.DROP_OLDEST) {
        log.warn("Drop oldest event $it")
    }

    private val loginLock = Mutex()
    lateinit var qqBot: Bot

    val jobLock: Mutex = Mutex()

    @OptIn(DelicateCoroutinesApi::class)
    private val workerContext = newFixedThreadPoolContext(10, "${qqProperties.account}-worker")
    private val defaultScope = CoroutineScope(parentCoroutineContext + workerContext)

    private fun buildMiraiBot(qrCodeLogin: Boolean = false): Bot {
        val configuration = BotConfiguration().apply {
            cacheDir = File("./mirai/${qqProperties.account}")
            fileBasedDeviceInfo("${bot.configPath}/device.json") // 使用 device.json 存储设备信息
            protocol = qqProperties.protocol // 切换协议
            highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
            parentCoroutineContext = this@QQBot.parentCoroutineContext
//            loginSolver = MultipleLoginSolver(bot)
        }
        log.info("协议版本检查更新...")
        try {
            FixProtocolVersion.update()
            FixProtocolVersion.sync(configuration.protocol)
            log.info("当前协议\n{}", FixProtocolVersion.info())
        } catch (cause: Throwable) {
            log.error("协议版本升级失败", cause)
        }
        return if (qrCodeLogin || qqProperties.password.isBlank()) {
            BotFactory.newBot(qqProperties.account, BotAuthorization.byQRCode(), configuration)
        } else {
            BotFactory.newBot(qqProperties.account, qqProperties.password, configuration)
        }
    }

    suspend fun start(waitForInit: Boolean = false) = loginLock.withLock {
        if (this::qqBot.isInitialized) {
            if (qqBot.isOnline) return@withLock
            if (qqBot.isActive) qqBot.close()
        }
        qqBot = buildMiraiBot()
        log.info("Login qq ${qqProperties.account}...")

        qqBot.login()
        status.update { Running }
        val initBot = suspend {
            qqBot.eventChannel.filter { event ->
                return@filter kotlin.runCatching {
                    bot.discord.incomingEventChannel.trySend(event)
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
                messageChannel.send(event)
            }

            messageChannel.receiveAsFlow().collect {
                runCatching {
                    handleEvent(it)
                }.onFailure {
                    log.error("Handle event error: ${it.message}", it)
                }
            }

            log.info("Started qq-bot ${qqBot.nick}(${qqBot.id})")
        }
        if (waitForInit) initBot()
        else defaultScope.launch { initBot() }
    }

    private suspend fun handleEvent(event: Event) {
        try {
            when (event) {
                is MessageEvent -> {
                    val json = event.message.serializeToJsonString()
                    when (event) {
                        is FriendMessageEvent -> {
                            qqMessageRepository.save(
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
                            val message = qqMessageRepository.save(
                                QQMessage(
                                    event.message.ids[0],
                                    event.bot.id,
                                    event.subject.id,
                                    event.sender.id,
                                    event.source.targetId,
                                    QQMessage.QQMessageType.GROUP_TEMP,
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

                        is GroupAwareMessageEvent -> {
                            val message = qqMessageRepository.save(
                                QQMessage(
                                    event.message.ids[0],
                                    event.bot.id,
                                    event.subject.id,
                                    event.sender.id,
                                    event.source.targetId,
                                    QQMessage.QQMessageType.GROUP,
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

//    suspend fun reportError(group: Group, messageChain: MessageChain, throwable: Throwable) {
//        log.error(throwable.message, throwable)
//        try {
////            val senderId = messageChain.source.fromId
////            val master = bot.getFriend(imSyncBot.userConfig.masterQQ)
////            master?.takeIf { it.id != 0L }?.sendMessage(
////                master.sendMessage(messageChain).quote()
////                    .plus("group: ${group.name}(${group.id}), sender: ${group[senderId]?.remarkOrNameCardOrNick}(${senderId})\n\n消息发送失败: (${throwable::class.simpleName}) ${throwable.message}")
////            )
//            kotlin.runCatching {
//                SendMessage(
//                    (bot.groupConfig.qqTg[group.id] ?: bot.groupConfig.defaultTgGroup).toString(),
//                    messageChain.contentToString()
//                ).send()
//            }.onFailure {
//                log.error("Report error fail.", it)
//            }.getOrNull()
//        } catch (e: Exception) {
//            log.error("Report error fail: ${e.message}", e)
//        }
//    }

    private suspend fun sendRemindMsg(event: GroupAwareMessageEvent) {
        if (bot.userConfig.masterUsername.isBlank()) return
        val content = event.message.filterIsInstance<PlainText>().map(PlainText::content).joinToString(separator = "")
        kotlin.runCatching {
            bot.tg.sendMessageText(
                TdApi.FormattedText().apply {
                    this.text =
                        "#提醒 #id${event.sender.id} #group${event.group.id}\n${event.group.name} - ${event.sender.nameCardOrNick}:\n$content"
                    this.entities = arrayOf(TextEntity().apply {
                        this.offset = 0
                        this.length = 3
                        this.type = TextEntityTypeMentionName(bot.userConfig.masterTg)
                    })
                },
                bot.groupConfig.qqTg[event.group.id] ?: bot.groupConfig.defaultTgGroup
            )
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


}