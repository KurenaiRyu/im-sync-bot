package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.repository.BindingGroupRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import javax.transaction.Transactional

@Component
class BindingGroupDelete(
    val repository: BindingGroupRepository,
    val bindingGroupConfig: BindingGroupConfig
) : Callback() {

    private val log = KotlinLogging.logger {}

    companion object {
        const val methodStr = "groupDelete"
    }

    override val method = methodStr

    @Transactional()
    override fun handle0(update: Update, message: Message): Int {
        val qq = getBody(update).toLong()
        val found = repository.findAll().takeIf { it.isNotEmpty() }?.first { it.qq == qq }
        if (found != null) {
            repository.delete(found)
        }
        bindingGroupConfig.changeToConfigs(message.messageId, message.chatId)
        return END
    }
}