package kurenai.imsyncbot.qq

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.config.BotProperties
import kurenai.imsyncbot.utils.BotUtil
import mu.KotlinLogging
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.BotEvent
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.GroupEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.util.concurrent.LinkedBlockingQueue

@Component
class QQBotClient(
    private val properties: QQBotProperties,
    private val botProperties: BotProperties,
    private val handlerHolder: HandlerHolder,
) : InitializingBean {

    private val log = KotlinLogging.logger {}
    val bot = BotFactory.newBot(properties.account, properties.password) {
        fileBasedDeviceInfo() // 使用 device.json 存储设备信息
        protocol = properties.protocol // 切换协议
        highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
        val file = File(BotConstant.LOG_FILE_PATH)
        redirectBotLogToFile(file)
        redirectNetworkLogToFile(file)
    }
    val msgQueue = LinkedBlockingQueue<Boolean>(20)

    override fun afterPropertiesSet() {
        CoroutineScope(Dispatchers.Default).launch {
            log.info { "Login qq bot..." }
            bot.login()
            log.info("Started qq-bot {}({})", bot.bot.nick, bot.id)
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
                        sendTgMsgString(event)
                        false
                    }
                }
            }

            filter.subscribeAlways<Event> {
                handle(QQContext(this@QQBotClient, ContextHolder.telegramBotClient, it))
            }
            bot.join()
        }
    }

    private suspend fun handle(context: QQContext) {
        for (handler in handlerHolder.currentQQHandlerList) {
            context.handler = handler
            try {
                msgQueue.put(true)
                handleMessage(context)
            } catch (e: Exception) {
                reportError(context, e)
            } finally {
                msgQueue.take()
            }
        }
    }

    @Throws(Exception::class)
    suspend fun handleMessage(context: QQContext) {
        if (context.event is GroupAwareMessageEvent) {
            context.handler?.onGroupMessage(context.event)
        } else if (context.event is MessageRecallEvent) {
            context.handler?.onRecall(context.event)
        }
    }

    suspend fun reportError(context: QQContext, e: Throwable) {
        val event = context.event
        if (event is GroupAwareMessageEvent) {
            val message = event.message
            val sender = event.sender
            val group = event.group
            val master = ContextHolder.qqBot.getFriend(ContextHolder.masterOfQQ)
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

    private fun sendTgMsgString(event: BotEvent) {
        if (event is GroupEvent) {
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().text(event.toString())
                    .chatId(BotUtil.getTgChatByQQ(event.group.id).toString()).build()
            )
        }
    }
}