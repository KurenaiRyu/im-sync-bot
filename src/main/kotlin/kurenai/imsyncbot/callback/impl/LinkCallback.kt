package kurenai.imsyncbot.callback.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.handler.tg.TgForwardHandler
import kurenai.imsyncbot.service.CacheService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.EntityType
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.MessageEntity
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class LinkCallback(val cacheService: CacheService, val forwardHandler: TgForwardHandler) : Callback() {

    private val log = KotlinLogging.logger {}

    override val method: String = "link"

    override fun handle0(update: Update, message: Message): Int {
        val user = message.entities.first { it.type == EntityType.TEXTMENTION }.user
        val qq = message.entities.first { it.type == EntityType.CODE }.text.toLong()
        val client = ContextHolder.telegramBotClient
        val param = update.callbackQuery.data.split(" ")[1]

        if (param == "cancel") {
            if (update.callbackQuery.from.id == message.replyToMessage.from.id || UserConfig.superAdmins.contains(update.callbackQuery.from.id)) {
                client.send(DeleteMessage(message.chatId.toString(), message.messageId))
            } else {
                client.send(AnswerCallbackQuery(update.callbackQuery.id).apply {
                    text = "只允许本人或超级管理员取消"
                    showAlert = true
                })
            }
        } else {
            if (UserConfig.superAdmins.contains(update.callbackQuery.from.id)) {
                UserConfig.link(user.id, qq, user.userName)
                client.send(EditMessageText("${user.firstName}已绑定qq[$qq]").apply {
                    chatId = message.chatId.toString()
                    messageId = message.messageId
                    entities = listOf(
                        MessageEntity(EntityType.TEXTMENTION, 0, user.firstName.length).apply { this.user = user },
                        MessageEntity(EntityType.CODE, user.firstName.length + 6, qq.toString().length),
                    )
                })
            } else {
                client.send(AnswerCallbackQuery(update.callbackQuery.id).apply {
                    text = "只允许超级管理员确认"
                    showAlert = true
                })
            }
        }
        return END
    }
}