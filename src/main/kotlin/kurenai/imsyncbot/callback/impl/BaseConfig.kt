package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.service.ConfigService
import kurenai.imsyncbot.utils.BotUtil
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class BaseConfig(
    val configService: ConfigService
) : Callback() {

    companion object {
        const val methodStr = "baseConfig"
    }

    override val method = methodStr

    override fun handle0(update: Update, message: Message): Int {
        changeToConfigs(message.messageId, message.chatId)
        return END
    }

    fun changeToConfigs(messageId: Int, chatId: Long) {
        val configs = configService.findAll()


        val markup = ArrayList<List<InlineKeyboardButton>>()
        markup.addAll(BotUtil.buildInlineMarkup(listOf(mapOf("返回" to Config.methodStr))))
        markup.addAll(BotUtil.buildInlineMarkup(configs.map { config ->
            mapOf(
                config.key to "${UpdateConfig.methodStr} ${config.key}",
                config.value to "${UpdateConfig.methodStr} ${config.key}",
                "清除" to "${DeleteConfig.methodStr} ${config.key}",
            )
        }))

        ContextHolder.telegramBotClient.send(EditMessageText().apply {
            text = "每一行是一个配置项，点击配置项更新或者清除。"
            this.messageId = Config.messageIds.getOrDefault(chatId, messageId)
            this.chatId = chatId.toString()
            replyMarkup = InlineKeyboardMarkup(markup)
        })

    }
}