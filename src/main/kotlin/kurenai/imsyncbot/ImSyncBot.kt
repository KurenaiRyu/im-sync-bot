package kurenai.imsyncbot

import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.*
import kurenai.imsyncbot.bot.discord.DiscordBot
import kurenai.imsyncbot.bot.qq.QQBot
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.configuration.AppGraph
import kurenai.imsyncbot.service.GroupConfigService
import kurenai.imsyncbot.service.UserConfigService
import net.mamoe.mirai.utils.LoggerAdapters
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import java.net.InetSocketAddress
import java.net.Proxy

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
    internal val tg: TelegramBot = TelegramBot(configProperties.bot, serverScope.coroutineContext)
    internal val qq: QQBot = QQBot(configProperties.bot, serverScope.coroutineContext)
    internal val discord: DiscordBot = DiscordBot(this)
    internal val appGraph by lazy { createGraph<AppGraph>() }
    internal val commandDispatcher by lazy { appGraph.commandDispatcher }

    init {
        //mirai使用log4j2
        LoggerAdapters.useLog4j2()

        if (configProperties.debug) {
            Configurator.setLevel("kurenai.imsyncbot", Level.DEBUG)
        }
        configProxy()
    }

    suspend fun start() {
        log.info("Start im-sync-bot ...")
        log.info("Telegram bot ${configProperties.bot.telegram.username}")
//            log.info("QQ bot ${configProperties.bot.qq.account}")
        tg.start()
        qq.start()
        discord.start()
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