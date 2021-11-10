package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.service.ConfigService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class DeleteConfig(
    private val configService: ConfigService,
    private val baseConfig: BaseConfig
) : Callback() {

    companion object {
        const val methodStr = "deleteConfig"
    }

    private val log = KotlinLogging.logger {}

    override val method: String = methodStr

    override fun handle0(update: Update, message: Message): Int {
        val key = getBody(update)
        configService.delete(key)
        if (Config.messageIds.contains(message.chatId)) {
            baseConfig.changeToConfigs(Config.messageIds[message.chatId]!!, message.chatId)
        } else {
            log.warn { "Can not found config message id." }
        }
        return END
    }
}