package kurenai.imsyncbot

import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.handler.qq.QQHandler
import kurenai.imsyncbot.handler.tg.TgForwardHandler
import mu.KotlinLogging

class HandlerHolder(
//初始化时处理器列表
    handlerList: List<Handler>,
) {
    private val log = KotlinLogging.logger {}

    val currentQQHandlerList = ArrayList<QQHandler>()
    val currentTgHandlerList = ArrayList<TgForwardHandler>()

    init {
        handlerList.sorted().takeIf { it.isNotEmpty() }?.forEach {
            when (it) {
                is QQHandler -> currentQQHandlerList.add(it)
                is TgForwardHandler -> currentTgHandlerList.add(it)
            }
        }

        log.info("current qq handler: {}", currentQQHandlerList.map(Handler::handleName))
        log.info("current tg handler: {}", currentTgHandlerList.map(Handler::handleName))
    }
}