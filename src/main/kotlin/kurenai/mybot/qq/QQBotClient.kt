package kurenai.mybot.qq

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.mybot.ContextHolder
import kurenai.mybot.HandlerHolder
import kurenai.mybot.config.aspect.Retry
import kurenai.mybot.handler.config.ForwardHandlerProperties
import mu.KotlinLogging
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class QQBotClient(
    private val properties: QQBotProperties,
    private val forwardProperties: ForwardHandlerProperties,
    private val handlerHolder: HandlerHolder
) {

    private val log = KotlinLogging.logger {}

    val bot = BotFactory.newBot(properties.account, properties.password) {
        fileBasedDeviceInfo() // 使用 device.json 存储设备信息
        protocol = properties.protocol // 切换协议
        redirectBotLogToDirectory()
    }


    @PostConstruct
    fun run() {
        CoroutineScope(Dispatchers.Default).launch {
            bot.login()
            log.info("Started qq-bot {}({})", bot.bot.nick, bot.id)
            ContextHolder.qqBotClient = this@QQBotClient
            val filter = bot.eventChannel.filter {

                val filterList = properties.filter.group
                // not filter if empty
                if (filterList.isEmpty()) {
                    return@filter true
                }

                if (it is GroupAwareMessageEvent) {
                    return@filter filterList.contains(it.group.id)
                } else if (it is MessageRecallEvent) {
                    return@filter true
                }

                return@filter false
            }

            filter.subscribeAlways<Event> {
                doHandle(QQContext(this@QQBotClient, ContextHolder.telegramBotClient!!, it))
            }
            bot.join()
        }
    }

    private suspend fun doHandle(context: QQContext) {
        for (handler in handlerHolder.currentHandlerList) {
            context.handler = handler
            try {
                doHandleMessage(context)
            } catch (e: Exception) {
                reportError(context, e)
            }
        }
    }

    @Retry(errorHandlerName = "reportError")
    suspend fun doHandleMessage(context: QQContext) {
        if (context.event is GroupAwareMessageEvent) {
            context.handler?.handleQQGroupMessage(context.qqBotClient, context.telegramBotClient, context.event)
        } else if (context.event is MessageRecallEvent) {
            context.handler?.handleRecall(context.qqBotClient, context.telegramBotClient, context.event)
        }
    }

    suspend fun reportError(context: QQContext, e: Exception) {
        val event = context.event
        if (event is GroupAwareMessageEvent) {
            val message = event.message
            val sender = event.sender
            val group = event.group
            val master = bot.getFriend(forwardProperties.masterOfQq)
            master?.sendMessage(
                master.sendMessage(message).quote()
                    .plus("group: ${group.name}(${group.id}), sender: ${sender.nameCardOrNick}(${sender.id})\n\n消息发送失败: ${e.message}")
            )
        }
    }
}