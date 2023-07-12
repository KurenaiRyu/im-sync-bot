package kurenai.imsyncbot

import kotlinx.coroutines.*
import kurenai.imsyncbot.bot.discord.DiscordBot
import kurenai.imsyncbot.bot.qq.QQBot
import kurenai.imsyncbot.bot.qq.QQMessageHandler
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.utils.childScopeContext
import java.io.File
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
    internal val configPath: String,
    internal val configProperties: ConfigProperties
) : CoroutineScope, AbstractCoroutineContextElement(ImSyncBot) {

    companion object Key : CoroutineContext.Key<ImSyncBot>

    internal val name: String = File(configPath).name.let { if (it == "config") "root" else it }
    override val coroutineContext: CoroutineContext = CoroutineName(name)
        .plus(this).childScopeContext()
        .apply {
            job.invokeOnCompletion {
                kotlin.runCatching {
                    instants.remove(this)
                }.onFailure {
                    if (it !is CancellationException) TelegramBot.log.error(it.message, it)
                }
            }
        }

    internal val proxy: Proxy? = configProxy()
    internal val userConfig: UserConfig = UserConfig(configPath, configProperties)
    internal val groupConfig: GroupConfig = GroupConfig(this, configPath)
    var qqMessageHandler: QQMessageHandler = QQMessageHandler(configProperties, this)
    internal val qq: QQBot = QQBot(coroutineContext, configProperties.bot.qq, this)
    internal val tg: TelegramBot = TelegramBot(coroutineContext, configProperties.bot.telegram, this)
    internal val discord: DiscordBot = DiscordBot(this)
//    internal val privateHandle = PrivateChatHandler(configProperties)

    init {
        //mirai使用log4j2
//        LoggerAdapters.useLog4j2()
//
//        if (configProperties.debug) {
//            Configurator.setLevel("kurenai.imsyncbot", Level.DEBUG)
//        }
        configProxy()
    }

    suspend fun start() {
        withContext(this@ImSyncBot.coroutineContext) {
            log.info("Start im-sync-bot $name ...")
            log.info("Telegram bot ${configProperties.bot.telegram.username}")
            log.info("QQ bot ${configProperties.bot.qq.account}")
            tg.start()
            qq.start()
            discord.start()
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