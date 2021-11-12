package kurenai.imsyncbot.handler.config

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.repository.BindingGroupRepository
import org.springframework.beans.factory.InitializingBean

class ForwardHandlerInitializer(
    private val properties: ForwardHandlerProperties,
    private val groupBindingGroupRepository: BindingGroupRepository,
) : InitializingBean {

    override fun afterPropertiesSet() {
        groupBindingGroupRepository.findAll().forEach {
            ContextHolder.qqTgBinding.putIfAbsent(it.qq, it.tg)
            ContextHolder.tgQQBinding.putIfAbsent(it.tg, it.qq)
        }
        ContextHolder.defaultQQGroup = properties.group.defaultQQ
        ContextHolder.defaultTgGroup = properties.group.defaultTelegram
        ContextHolder.masterOfQQ = properties.masterOfQq
        ContextHolder.masterOfTg = properties.masterOfTg
    }
}