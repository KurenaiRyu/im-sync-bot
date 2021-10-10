package kurenai.imsyncbot.qq

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.config.BotProperties
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
            val filter = GlobalEventChannel.filter { event ->

                return@filter when (event) {
                    is GroupAwareMessageEvent -> {
                        val groupId = event.group.id
                        if (properties.filter.group.isNotEmpty() && !properties.filter.group.contains(groupId)) {
                            false
                        } else {
                            !botProperties.ban.group.contains(groupId) && !botProperties.ban.member.contains(event.sender.id)
                        }

                    }
                    else -> {
                        true
                    }
                }
            }

            filter.subscribeAlways<Event> { event ->
                CoroutineScope(Dispatchers.Default).launch {
                    when (event) {
                        is FriendEvent -> {
                            privateChatHandler.onFriendEvent(event)
                        }
                        is MessageEvent -> {
                            handle(event)
                        }
                        is MessageRecallEvent -> {
                            forwardHandler.onRecall(event)
                        }
                        is GroupEvent -> {
                            forwardHandler.onGroupEvent(event)
                        }
                        else -> {
                            log.debug { "未支持事件 ${event.javaClass} 的处理" }
                        }
                    }
                }
            }
            bot.join()
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
                ContextHolder.telegramBotClient.send(
                    SendMessage.builder().chatId(BotUtil.getTgChatByQQ(event.group.id).toString()).text(event.message.contentToString())
                        .build()
                )
            }
        }
    }

    private suspend fun sendTgMsgString(event: BotEvent) {
        if (event is GroupEvent) {
            ContextHolder.telegramBotClient.send(
                SendMessage.builder().text(event.toString())
                    .chatId(BotUtil.getTgChatByQQ(event.group.id).toString()).build()
            )
        }
    }
}