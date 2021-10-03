package kurenai.imsyncbot.qq

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.config.BotProperties
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.PrivateChatHandler
import kurenai.imsyncbot.handler.qq.QQForwardHandler
import kurenai.imsyncbot.telegram.TelegramBotClient
import kurenai.imsyncbot.utils.BotUtil
import mu.KotlinLogging
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.*
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@Component
class QQBotClient(
    private val properties: QQBotProperties,
    private val botProperties: BotProperties,
    private val handlerHolder: HandlerHolder,
    private val privateChatHandler: PrivateChatHandler,
) : InitializingBean {

    private val log = KotlinLogging.logger {}
    private val forwardHandler = handlerHolder.currentQQHandlerList.filterIsInstance<QQForwardHandler>()[0]

    @OptIn(ObsoleteCoroutinesApi::class)
    private val eventActor = CoroutineScope(Dispatchers.IO).actor<Event> {
        for (msg in channel) {
            doSubscribe(msg)
        }
    }
    val bot = BotFactory.newBot(properties.account, properties.password) {
        fileBasedDeviceInfo() // 使用 device.json 存储设备信息
        protocol = properties.protocol // 切换协议
        highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
        val file = File(BotConstant.LOG_FILE_PATH)
        redirectBotLogToFile(file)
        redirectNetworkLogToFile(file)
    }

    override fun afterPropertiesSet() {
        CoroutineScope(Dispatchers.Default).launch {
            log.info { "Login qq bot..." }
            bot.login()
            log.info { "Started qq-bot ${bot.nick}(${bot.id})" }
            ContextHolder.qqBot = bot
            val filter = bot.eventChannel.filter { event ->

                return@filter when (event) {
                    is GroupAwareMessageEvent -> {
                        val groupId = event.group.id
                        properties.filter.group.takeIf { it.isNotEmpty() }?.contains(groupId) != false
                                && !botProperties.ban.group.contains(groupId)
                                && !botProperties.ban.member.contains(event.sender.id)
                    }
                    is MessageRecallEvent -> {
                        true
                    }
                    else -> {
                        false
                    }
                }
            }

            filter.subscribeAlways<Event> {
                when (this) {
                    is GroupEvent -> {
                        forwardHandler.onGroupEvent(this)
                    }
                    else -> {
                        eventActor.send(this)
                    }
                }
            }
            bot.join()
        }
    }

    private suspend fun doSubscribe(event: Event) {
        try {
            when (event) {
                is FriendEvent -> {
                    privateChatHandler.onFriendEvent(event)
                }
                else -> {
                    handle(this@QQBotClient, ContextHolder.telegramBotClient, event)
                }
            }
        } catch (e: Exception) {
            reportError(event, e)
        }
    }

    private suspend fun handle(client: QQBotClient, tgClient: TelegramBotClient, event: Event) {
        for (handler in handlerHolder.currentQQHandlerList) {
            val context = QQContext(client, tgClient, event, handler)
            if (handleMessage(context) == END) break
        }
    }

    @Throws(Exception::class)
    suspend fun handleMessage(context: QQContext): Int {
        return when (context.event) {
            is GroupAwareMessageEvent -> {
                context.handler.onGroupMessage(context.event)
            }
            is MessageRecallEvent -> {
                context.handler.onRecall(context.event)
            }
            else -> {
                CONTINUE
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
                ContextHolder.telegramBotClient.execute(
                    SendMessage.builder().chatId(BotUtil.getTgChatByQQ(event.group.id).toString()).text(event.message.contentToString())
                        .build()
                )
            }
        }
    }

    private fun sendTgMsgString(event: BotEvent) {
        if (event is GroupEvent) {
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().text(event.toString())
                    .chatId(BotUtil.getTgChatByQQ(event.group.id).toString()).build()
            )
        }
    }
}