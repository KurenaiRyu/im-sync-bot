package kurenai.imsyncbot

import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.handler.qq.QQHandler
import kurenai.imsyncbot.handler.tg.TgMessageHandler
import mu.KotlinLogging
import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Instance

@ApplicationScoped
class HandlerHolder(
//初始化时处理器列表
    val handlerList: Instance<Handler>,
) {
    private val log = KotlinLogging.logger {}

    final val currentQQHandlerList = ArrayList<QQHandler>()
    final val currentTgHandlerList = ArrayList<TgMessageHandler>()

    @PostConstruct
    fun initHandlers() {
        handlerList.sorted().takeIf { it.isNotEmpty() }?.forEach {
            when (it) {
                is QQHandler -> currentQQHandlerList.add(it)
                is TgMessageHandler -> currentTgHandlerList.add(it)
            }
        }

        log.info("current qq handler: {}", currentQQHandlerList.map(Handler::handleName))
        log.info("current tg handler: {}", currentTgHandlerList.map(Handler::handleName))
    }
}