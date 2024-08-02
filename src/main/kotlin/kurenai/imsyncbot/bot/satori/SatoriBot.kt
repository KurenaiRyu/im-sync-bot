package kurenai.imsyncbot.bot.satori

import com.github.nyayurn.yutori.Adapter
import com.github.nyayurn.yutori.GlobalLoggerFactory
import com.github.nyayurn.yutori.Satori
import com.github.nyayurn.yutori.module.adapter.satori.Satori
import com.github.nyayurn.yutori.satori
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kurenai.imsyncbot.*
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.utils.getLogger
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

//TODO: satori 貌似是一个链接拿到多个账号消息，需要做改变，目前为单账号
class SatoriBot(
    val bot: ImSyncBot
) : CoroutineScope {

    companion object {
        val log = getLogger()
    }

    init {
        GlobalLoggerFactory.factory = SatoriLoggerFactory()
        if (configProperties.debug) {
            Configurator.setLevel("com.github.nyayurn.yutori", Level.DEBUG)
        }
    }

    private val satoriConfig = bot.configProperties.bot.satori

    override val coroutineContext: CoroutineContext = bot.coroutineContext
        .plus(SupervisorJob(bot.coroutineContext[Job]))
        .plus(CoroutineExceptionHandler { context, exception ->
            when (exception) {
                is CancellationException -> {
                    log.warn("{} was cancelled", context[CoroutineName])
                }

                else -> {
                    log.warn("with {}", context[CoroutineName], exception)
                }
            }
        })

    val telegramBot: TelegramBot = bot.tg
    var satori: Satori? = null
    val handle = SatoriHandler(configProperties)

    val status = MutableStateFlow<BotStatus>(Initializing)
    var restartCount = 0
    val messageChannel = Channel<suspend () -> Unit>(capacity = Channel.Factory.UNLIMITED)

    fun start() {
        if (satori != null) {
            return
        }

        satori = buildSatori()

        launch {
            for (func in messageChannel) {
                kotlin.runCatching { func() }.onFailure {
                    log.error("{}", it.localizedMessage)
                }
            }
        }

        satori!!.start()
    }

    fun buildSatori() = satori {
        install(Adapter.Companion.Satori) {
            host = satoriConfig.host ?: "localhost"
            port = satoriConfig.port ?: 5500
            satoriConfig.token?.let { token = it }
            satoriConfig.path?.let { path = it }

            onConnect { _, _, _ ->
                status.update { Running }
                restartCount = 0
            }

            onError {
                status.update { Stopped }
            }
        }

        listening {
            message.created {
                launch {
                    messageChannel.send {
                        handle.onMessage(actions, event, satori, telegramBot)
                    }
                }
            }
        }
    }
}

