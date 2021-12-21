package kurenai.imsyncbot.handler.config

import kurenai.imsyncbot.ContextHolder
import org.springframework.beans.factory.InitializingBean

class ForwardHandlerInitializer(
    private val properties: ForwardHandlerProperties,
) : InitializingBean {

    override fun afterPropertiesSet() {
        ContextHolder.defaultQQGroup = properties.group.defaultQQ
        ContextHolder.defaultTgGroup = properties.group.defaultTelegram
        ContextHolder.masterOfQQ = properties.masterOfQq
        ContextHolder.masterOfTg = properties.masterOfTg
        ContextHolder.masterUsername = properties.masterNameOfTg
    }
}