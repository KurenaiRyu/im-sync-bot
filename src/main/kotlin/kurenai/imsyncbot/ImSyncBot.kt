package kurenai.imsyncbot

import kotlinx.coroutines.*
import kurenai.imsyncbot.bot.qq.QQBot
import kurenai.imsyncbot.bot.qq.QQMessageHandler
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.service.GroupConfigService
import kurenai.imsyncbot.service.UserConfigService
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * @author Kurenai
 * @since 2022/10/3 8:48
 */

class ImSyncBot(
    internal val configProperties: ConfigProperties
) : CoroutineScope, AbstractCoroutineContextElement(ImSyncBot) {

    companion object Key : CoroutineContext.Key<ImSyncBot>

    override val coroutineContext: CoroutineContext = this.plus(CoroutineName("im-sync-bot"))
        .plus(SupervisorJob())
        .apply {
            job.invokeOnCompletion {
                kotlin.runCatching {
                    instants.remove(this)
                }.onFailure {
                    if (it !is CancellationException) TelegramBot.log.error(it.message, it)
                }
            }
        }

    internal val userConfigService: UserConfigService = UserConfigService(configProperties)
    internal val groupConfigService: GroupConfigService = GroupConfigService(this, "./config")
    internal val tg: TelegramBot = TelegramBot(configProperties.bot.telegram, this)
    internal var qqMessageHandler: QQMessageHandler = QQMessageHandler(configProperties, this)
    internal val qq: QQBot = QQBot(configProperties.bot.qq, this)
//    internal val satori: SatoriBot = SatoriBot(this)
//    internal val discord: DiscordBot = DiscordBot(this)
//    internal val privateHandle = PrivateChatHandler(configProperties)

    init {
        if (configProperties.debug) {
            Configurator.setLevel("kurenai.imsyncbot", Level.DEBUG)
        }
        configProxy()
    }

    suspend fun start() {
        withContext(this@ImSyncBot.coroutineContext) {
            log.info("Start im-sync-bot ...")
            log.info("Telegram bot ${configProperties.bot.telegram.username}")
//            log.info("QQ bot ${configProperties.bot.qq.account}")
            tg.start()
            qq.start()
//            satori.start()
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

suspend fun getBot(): ImSyncBot? = runCatching { getBotOrThrow() }.getOrNull()
suspend fun getBotOrThrow(): ImSyncBot = coroutineContext[ImSyncBot] ?: throw IllegalArgumentException("找不到当前Bot实例")