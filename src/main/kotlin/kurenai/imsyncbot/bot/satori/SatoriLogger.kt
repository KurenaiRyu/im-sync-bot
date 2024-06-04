package kurenai.imsyncbot.bot.satori

import com.github.nyayurn.yutori.Level
import com.github.nyayurn.yutori.Logger
import com.github.nyayurn.yutori.LoggerFactory
import kurenai.imsyncbot.utils.getLogger

class SatoriLogger(
    private val clazz: Class<*>
) : Logger {

    val log = getLogger(clazz.name)

    override fun log(level: Level, service: String, msg: String) {
        when (level) {
            Level.ERROR -> log.error(msg)
            Level.WARN -> log.warn(msg)
            Level.INFO -> log.info(msg)
            Level.DEBUG -> log.debug(msg)
        }
    }
}

class SatoriLoggerFactory : LoggerFactory {
    override fun getLogger(clazz: Class<*>): Logger {
        return SatoriLogger(clazz)
    }
}