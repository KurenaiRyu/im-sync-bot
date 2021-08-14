package kurenai.mybot.config

import kurenai.mybot.BotConfigConstant
import kurenai.mybot.ContextHolder
import kurenai.mybot.repository.BotConfigRepository
import org.springframework.beans.factory.InitializingBean

class BotInitializer(
    private val botConfigRepository: BotConfigRepository
) : InitializingBean {

    override fun afterPropertiesSet() {
        botConfigRepository.getByKey(BotConfigConstant.MASTER_CHAT_ID).value.takeIf { it.isNotBlank() }?.let {
            ContextHolder.masterChatId = it.toLong()
        }
    }
}