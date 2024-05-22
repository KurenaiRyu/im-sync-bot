package kurenai.imsyncbot.bot.qq

import com.github.nyayurn.yutori.Adapter
import com.github.nyayurn.yutori.Satori
import com.github.nyayurn.yutori.module.adapter.satori.Satori
import com.github.nyayurn.yutori.module.adapter.satori.SatoriAdapter
import com.github.nyayurn.yutori.module.chronocat.ChronocatModule
import com.github.nyayurn.yutori.satori
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kurenai.imsyncbot.BotStatus
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.Initializing
import kurenai.imsyncbot.Running
import kurenai.imsyncbot.Stopped
import kurenai.imsyncbot.bot.telegram.TelegramBot

//TODO: satori 貌似是一个链接拿到多个账号消息，需要做改变，目前为单账号
class SatoriBot(
    val telegramBot: TelegramBot,
    val configProperties: ConfigProperties
) {

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
            host = "localhost"
            port = 6700

            onConnect { _,_,_ ->
                status.update {  Running }
                restartCount = 0
            }

            onError {
                status.update { Stopped }

                GlobalScope.launch {
                    restart()
                }
            }
        }

        listening {
            any {
                runBlocking {
                    handle.onGroup(actions, event, satori, telegramBot)
                }
            }
        }
    }
}

