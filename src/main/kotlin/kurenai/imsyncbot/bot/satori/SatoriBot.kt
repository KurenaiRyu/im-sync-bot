package kurenai.imsyncbot.bot.satori

import com.github.nyayurn.yutori.Adapter
import com.github.nyayurn.yutori.Satori
import com.github.nyayurn.yutori.module.adapter.satori.Satori
import com.github.nyayurn.yutori.satori
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kurenai.imsyncbot.*
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.utils.getLogger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

//TODO: satori 貌似是一个链接拿到多个账号消息，需要做改变，目前为单账号
class SatoriBot(
    val bot: ImSyncBot
) : CoroutineScope {

    companion object {
        val log = getLogger()
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

    fun start() {
        if (satori != null) {
            return
        }

        satori = buildSatori()

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
                    handle.onMessage(actions, event, satori, telegramBot)
                }
            }
        }
    }
}

