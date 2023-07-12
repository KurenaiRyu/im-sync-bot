package moe.fuqiuluo.unidbg.session

import kurenai.imsyncbot.bot.qq.login.qsign.EnvData
import java.util.concurrent.ConcurrentHashMap

object SessionManager {
    private val sessionMap = ConcurrentHashMap<Long, Session>()

    operator fun get(uin: Long): Session? {
        return sessionMap[uin]
    }

    operator fun contains(uin: Long) = sessionMap.containsKey(uin)

    fun register(envData: EnvData) {
        if (envData.uin in this) {
            close(envData.uin)
        }
        sessionMap[envData.uin] = Session(envData)
    }

    fun close(uin: Long) {
        sessionMap[uin]?.vm?.destroy()
    }
}