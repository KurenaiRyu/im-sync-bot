package kurenai.mybot.qq

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.mybot.BotConstant
import kurenai.mybot.ContextHolder
import kurenai.mybot.HandlerHolder
import kurenai.mybot.config.BotProperties
import kurenai.mybot.utils.BotUtil
import kurenai.mybot.utils.RetryUtil
import mu.KotlinLogging
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.BotEvent
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.GroupEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.io.File
import javax.annotation.PostConstruct

@Component
class QQBotClient(
    private val properties: QQBotProperties,
    private val botProperties: BotProperties,
    private val handlerHolder: HandlerHolder
) {

    private val log = KotlinLogging.logger {}

    val bot = BotFactory.newBot(properties.account, properties.password) {
        fileBasedDeviceInfo() // 使用 device.json 存储设备信息
        protocol = properties.protocol // 切换协议
        highwayUploadCoroutineCount = Runtime.getRuntime().availableProcessors() * 2
        redirectNetworkLogToFile(File(BotConstant.LOG_FILE_PATH))
    }


    @PostConstruct
    fun run() {
        CoroutineScope(Dispatchers.Default).launch {
            bot.login()
            log.info("Started qq-bot {}({})", bot.bot.nick, bot.id)
            ContextHolder.qqBotClient = this@QQBotClient
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
                doHandle(QQContext(this@QQBotClient, ContextHolder.telegramBotClient, it))
            }
            bot.join()
        }
    }

    private suspend fun doHandle(context: QQContext) {
        for (handler in handlerHolder.currentHandlerList) {
            context.handler = handler
            RetryUtil.aware({ doHandleMessage(context) }, { _, e ->
                e?.let { reportError(context, it) }
            })
        }
    }

    @Throws(Exception::class)
    suspend fun doHandleMessage(context: QQContext) {
        if (context.event is GroupAwareMessageEvent) {
            context.handler?.handleQQGroupMessage(context.event)
        } else if (context.event is MessageRecallEvent) {
            context.handler?.handleQQRecall(context.event)
        }
    }

    suspend fun reportError(context: QQContext, e: Throwable) {
        val event = context.event
        if (event is GroupAwareMessageEvent) {
            val message = event.message
            val sender = event.sender
            val group = event.group
            val master = bot.getFriend(ContextHolder.masterOfQQ)
            master?.takeIf { it.id != 0L }?.sendMessage(
                master.sendMessage(message).quote()
                    .plus("group: ${group.name}(${group.id}), sender: ${sender.nameCardOrNick}(${sender.id})\n\n消息发送失败: ${e.message}")
            )
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().chatId(BotUtil.getQQGroupByTg(event.group.id).toString()).text(event.message.contentToString())
                    .build()
            )
        }
    }

    private fun sendTgMsgString(event: BotEvent) {
        if (event is GroupEvent) {
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().text(event.toString())
                    .chatId(BotUtil.getQQGroupByTg(event.group.id).toString()).build()
            )
        }
    }
}