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
class UnbanPicCommand(
    val cacheService: CacheService
) : AbstractCommand() {

    override val command = "unbindPic"
    override val help: String = "解除排除群或用户图片消息"
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String {
        return if (message.isReply) {
            val user = message.replyToMessage.from
            if (user.isBot && user.userName == ContextHolder.telegramBotClient.botUsername) {
                val qqMsg = cacheService.getQQByTg(message.replyToMessage)
                if (qqMsg != null) {
                    UserConfig.unbanPic(qqMsg.fromId)
                    "qq[${qqMsg.fromId}] 已正常转发"
                } else "找不到该qq信息"
            } else {
                UserConfig.unbanPic(user.id)
                "${user.firstName} 已正常转发"
            }
        } else {
            GroupConfig.unbanPic(message.chatId)
            return "群消息已正常转发"
        }
    }

}