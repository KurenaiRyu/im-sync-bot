package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.config.UserStatus
import kurenai.imsyncbot.service.CacheService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.EntityType
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.MessageEntity
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class LinkCommand(
    val cacheService: CacheService,
) : AbstractCommand() {

    override val command = "link"
    override val help: String = "链接qq与tg关系，能够在AT时被提醒（非超管将需要被确认）"

    override val onlySupperAdmin = false
    override val onlyGroupMessage = true
    override val onlyReply = true

    override val reply = true

    override fun execute(update: Update, message: Message): String? {
        val client = ContextHolder.telegramBotClient
        if (message.replyToMessage.from.userName != client.botUsername) return "请引用转发的qq消息"
        val qqMsg = cacheService.getQQByTg(message.replyToMessage) ?: return "找不到该qq信息"
        return if (UserConfig.superAdmins.contains(message.from.id)) {
            val user = UserConfig.links.firstOrNull { it.tg == message.from.id && it.qq != null }
            if (user != null) {
                return if (user.status.contains(UserStatus.MASTER)) "master账号无法改变绑定qq"
                else "qq[${qqMsg.fromId}]已绑定@${user.username}"
            }

            message.entities.firstOrNull { it.type == EntityType.MENTION }?.text?.substring(1)?.let {
                GetChatMember()
            }

            UserConfig.link(message.from.id, qqMsg.fromId, message.from.userName)
            "绑定qq[${qqMsg.fromId}]成功"
        } else {
            UserConfig.link(message.from.id, qqMsg.fromId, message.from.userName)
            client.send(SendMessage(message.chatId.toString(), "用户准备绑定 qq[${qqMsg.fromId}]").apply {
                replyToMessageId = message.messageId
                entities = listOf(
                    MessageEntity(EntityType.TEXTMENTION, 0, 2).apply { this.user = message.from },
                    MessageEntity(EntityType.CODE, 10, qqMsg.fromId.toString().length),
                )
                replyMarkup = InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton("确认").apply { this.callbackData = "link ${qqMsg.fromId}" },
                            InlineKeyboardButton("取消").apply { this.callbackData = "link cancel" },
                        )
                    )
                )
            })
            null
        }
    }
}