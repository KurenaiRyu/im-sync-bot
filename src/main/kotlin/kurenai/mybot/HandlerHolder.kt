package kurenai.mybot

import kurenai.mybot.handler.Handler
import mu.KotlinLogging

open class HandlerHolder(
//初始化时处理器列表
    handlerList: List<Handler>,
) {
    lateinit var handlerList: List<Handler>
    private val log = KotlinLogging.logger {}

    val currentHandlerList: List<Handler> = handlerList.sorted()

    init {
        log.info("current handler: {}", currentHandlerList.map(Handler::handleName))
    }
}