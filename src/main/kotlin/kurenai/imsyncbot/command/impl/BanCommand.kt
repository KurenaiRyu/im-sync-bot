package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.BanConfig
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.service.CacheService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class BanCommand(
    val cacheService: CacheService,
) : AbstractCommand() {

    override val command = "ban"
    override val help: String = "排除群或用户消息（但事件仍会接受）"
    override val onlyAdmin = true
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String? {
        return if (message.isReply) {
            val user = message.from
            if (user.isBot && user.userName == ContextHolder.telegramBotClient.botUsername) {
                val qqMsg = cacheService.getQQByTg(message.replyToMessage)
                if (qqMsg != null) {
                    BanConfig.addId(qqMsg.fromId)
                    "qq[`${qqMsg.fromId}`] 已排除转发"
                } else "找不到该qq信息"
            } else {
                BanConfig.addId(user.id)
                "`${user.firstName}` 已排除转发"
            }
        } else {
            GroupConfig.ban(message.chatId)
            "排除群信息设置成功"
        }
    }
}