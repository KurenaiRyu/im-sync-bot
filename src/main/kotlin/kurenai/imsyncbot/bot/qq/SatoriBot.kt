package kurenai.imsyncbot.bot.qq

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
    val bot: ImSyncBot,
    val configProperties: ConfigProperties
) : CoroutineScope {

    companion object {
        val log = getLogger()
    }

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

    fun restart() {
        if (restartCount >= 3) error("Restart fail over 3 times!")
        else restartCount++

        satori?.stop()

        satori = buildSatori()

        satori!!.start()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun buildSatori() = satori {
        install(Adapter.Companion.Satori) {
            host = configProperties.bot.qq.host ?: "localhost"
            port = configProperties.bot.qq.port ?: 5500
            configProperties.bot.qq.token?.let { token = it }
            configProperties.bot.qq.path?.let { path = it }

            onConnect { _, _, _ ->
                status.update { Running }
                restartCount = 0
            }

            onError {
                status.update { Stopped }

                launch {
                    restart()
                }
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

