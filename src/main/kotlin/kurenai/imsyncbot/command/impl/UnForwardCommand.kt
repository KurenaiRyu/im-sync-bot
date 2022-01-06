package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class UnForwardCommand(
    val cacheService: CacheService,
) : AbstractCommand() {

    override val command = "unfwd"
    override val help: String = "排除群或用户消息（但事件仍会接受）"
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String? {
        return if (message.isReply) {
            val user = message.replyToMessage.from
            if (user.isBot && user.userName == ContextHolder.telegramBotClient.botUsername) {
                val qqMsg = cacheService.getQQByTg(message.replyToMessage)
                if (qqMsg != null) {
                    UserConfig.ban(qq = qqMsg.fromId)
                    "qq[${qqMsg.fromId}] 已排除转发"
                } else "找不到该qq信息"
            } else {
                UserConfig.ban(user.id)
                "${user.firstName} 已排除转发"
            }
        } else {
            GroupConfig.ban(message.chatId)
            "排除群信息设置成功"
        }
    }
}