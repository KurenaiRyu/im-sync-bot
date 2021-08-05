package kurenai.mybot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.mybot.handler.config.ForwardHandlerProperties
import kurenai.mybot.qq.QQBotProperties
import kurenai.mybot.utils.RetryUtil
import mu.KotlinLogging
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupMessageSyncEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.data.MessageChain
import org.springframework.context.ApplicationContext
import javax.annotation.PostConstruct

class QQBotClient(private val properties: QQBotProperties, private val forwardProperties: ForwardHandlerProperties, private val handlerHolder: HandlerHolder, private val context: ApplicationContext) {

    private val log = KotlinLogging.logger {}

    val bot: Bot = BotFactory.newBot(properties.account, properties.password) {
        fileBasedDeviceInfo() // 使用 device.json 存储设备信息
        protocol = properties.protocol // 切换协议
        redirectBotLogToDirectory()
    }


    @PostConstruct
    fun run() {
        CoroutineScope(Dispatchers.Default).launch {
            bot.login()
            log.info("Started qq-bot {}({})", bot.bot.nick, bot.id)
            val filter = bot.eventChannel.filter {

                val filterList = properties.filter.group
                // not filter if empty
                if (filterList.isEmpty()) {
                    return@filter true
                }

                if (it is GroupMessageEvent) {
                    return@filter filterList.contains(it.group.id)
                } else if (it is GroupMessageSyncEvent) {
                    return@filter filterList.contains(it.group.id)
                }

                return@filter false
            }

            val telegramBotClient = context.getBean(TelegramBotClient::class.java)
            filter.subscribeAlways<GroupAwareMessageEvent> {
                for (handler in handlerHolder.currentHandlerList) {
                    try {
                        if (!RetryUtil.retry(3) { handler.handleQQGroupMessage(this@QQBotClient, telegramBotClient, it) }) break
                    } catch (e: Exception) {
                        log.error(e.message, e)
                        reportError(it.message, it.group, it.sender, e.message)
                    }
                }
            }
            filter.subscribeAlways<MessageRecallEvent> {
                for (handler in handlerHolder.currentHandlerList) {
                    try {
                        if (!RetryUtil.retry(3) { handler.handleRecall(this@QQBotClient, telegramBotClient, it) }) break
                    } catch (e: Exception) {
                        log.error(e.message, e)
                    }
                }
            }
            bot.join()
        }
    }

    private suspend fun reportError(message: MessageChain, group: Group, sender: User, errorMsg: String?) {
        val master = bot.getFriend(forwardProperties.masterOfQq)
        master?.sendMessage(master.sendMessage(message).quote()
            .plus("group: ${group.name}(${group.id}), sender: ${sender.nameCardOrNick}(${sender.id})\n\n消息发送失败: $errorMsg"))
    }
}