package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import org.springframework.stereotype.Component

@Component
class UnbanPicCommand(
    val cacheService: CacheService
) : AbstractTelegramCommand() {

    override val command = "unbanPic"
    override val help: String = "解除排除群或用户图片消息"
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String {
        return if (message.isReply()) {
            val reply = message.replyToMessage!!
            val user = reply.from!!
            if (user.isBot && user.username == ContextHolder.telegramBot.username) {
                val qqMsg = cacheService.getQQByTg(reply)
                if (qqMsg != null) {
                    UserConfig.unbanPic(qqMsg.fromId)
                    "qq[${qqMsg.fromId}] 已正常转发"
                } else "找不到该qq信息"
            } else {
                UserConfig.unbanPic(user.id)
                "${user.firstName} 已正常转发"
            }
        } else {
            GroupConfig.unbanPic(message.chat.id)
            return "群消息图片已正常转发"
        }
    }

}