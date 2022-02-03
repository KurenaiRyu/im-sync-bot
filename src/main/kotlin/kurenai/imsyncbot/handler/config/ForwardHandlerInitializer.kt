package kurenai.imsyncbot.handler.config

import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import org.springframework.beans.factory.InitializingBean

class ForwardHandlerInitializer(
    private val properties: ForwardHandlerProperties,
) : InitializingBean {

    override fun afterPropertiesSet() {
        GroupConfig.defaultQQGroup = properties.group.defaultQQ
        GroupConfig.defaultTgGroup = properties.group.defaultTelegram
        UserConfig.setMaster(properties)
    }
}