package kurenai.imsyncbot

import kotlinx.coroutines.*
import kurenai.imsyncbot.bot.qq.QQBot
import kurenai.imsyncbot.bot.qq.QQMessageHandler
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.service.GroupConfigService
import kurenai.imsyncbot.service.UserConfigService
import net.mamoe.mirai.utils.LoggerAdapters
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.collections.remove
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * @author Kurenai
 * @since 2022/10/3 8:48
 */

class ImSyncBot(
    internal val configProperties: ConfigProperties
) {

    private val serverScope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.Default +
                CoroutineName("ImSyncBot") +
                CoroutineExceptionHandler { ctx, ex ->
                    runCatching {
                        kotlin.runCatching {
                            instants.remove(this)
                        }.onFailure {
                            if (it !is CancellationException) TelegramBot.log.error(it.message, it)
                        }
                    }
                })

    internal val userConfigService: UserConfigService = UserConfigService(configProperties)
    internal val groupConfigService: GroupConfigService = GroupConfigService(this)
    internal val tg: TelegramBot = TelegramBot(configProperties.bot.telegram, this)
    internal var qqMessageHandler: QQMessageHandler = QQMessageHandler(configProperties, this)
    internal val qq: QQBot = QQBot(configProperties.bot.qq, serverScope.coroutineContext[Job], this)
//    internal val discord: DiscordBot = DiscordBot(this)

    init {
        //mirai使用log4j2
        LoggerAdapters.useLog4j2()

        if (configProperties.debug) {
            Configurator.setLevel("kurenai.imsyncbot", Level.DEBUG)
        }
        configProxy()
    }

    fun start() {
        serverScope.launch {
            log.info("Start im-sync-bot ...")
            log.info("Telegram bot ${configProperties.bot.telegram.username}")
//            log.info("QQ bot ${configProperties.bot.qq.account}")
            tg.start()
            qq.start()
//            discord.start()
        }
    }

    private fun configProxy(): Proxy? {
        val configProxy = configProperties.bot.telegram.proxy ?: return null
        return if (configProxy.type != Proxy.Type.DIRECT) {
            Proxy(configProxy.type, InetSocketAddress(configProxy.host, configProxy.port))
        } else {
            null
        }
    }
}