package kurenai.mybot

import kurenai.mybot.qq.QQBotProperties
import mu.KotlinLogging
import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupMessageSyncEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import org.springframework.context.ApplicationContext
import java.util.concurrent.Executors

class QQBotClient(private val properties: QQBotProperties, private val handlerHolder: HandlerHolder, private val context: ApplicationContext) {

    private val log = KotlinLogging.logger {}

    val bot: Bot = BotFactory.newBot(properties.account, properties.password) {
        fileBasedDeviceInfo() // 使用 device.json 存储设备信息
        protocol = properties.protocol // 切换协议
    }

    private fun run() {

        val filter = bot.eventChannel.filter {

            val filterList = properties.filter.qq
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

        val pool = Executors.newFixedThreadPool(5)

        val telegramBotClient = context.getBean(TelegramBotClient::class.java);
        filter.subscribeAlways<GroupAwareMessageEvent> {
            pool.execute {
                for (handler in handlerHolder.currentHandlerList) {
                    try {
                        if (!handler.handle(this@QQBotClient, telegramBotClient, it)) break
                    } catch (e: Exception) {
                        log.error(e.message, e)
                    }
                }
            }
        }
        filter.subscribeAlways<MessageRecallEvent> {
            pool.execute {
                for (handler in handlerHolder.currentHandlerList) {
                    try {
                        if (!handler.handleRecall(this@QQBotClient, telegramBotClient, it)) break
                    } catch (e: Exception) {
                        log.error(e.message, e)
                    }
                }
            }
        }
    }

    init {
        suspend {
            // 使用自定义配置
            val pool = Executors.newFixedThreadPool(1)
            bot.alsoLogin()
            log.info("Started qq-bot {}({})", bot.bot.nick, bot.id)
            pool.execute { run() }
        }
    }
}