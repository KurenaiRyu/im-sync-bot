package kurenai.mybot.utils

import kurenai.mybot.ContextHolder

object BotUtil {

    fun getQQGroupByTg(id: Long): Long {
        return ContextHolder.tgQQBinding[id] ?: ContextHolder.defaultQQGroup
    }

}