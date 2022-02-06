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
class ForwardCommand(
    val cacheService: CacheService
) : AbstractTelegramCommand() {

    override val command = "fwd"
    override val help: String = "解除排除群或用户消息"
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String {
        return if (message.isReply()) {
            val user = message.replyToMessage!!.from!!
            if (user.isBot && user.username == ContextHolder.telegramBot.username) {
                val qqMsg = cacheService.getQQByTg(message.replyToMessage!!)
                if (qqMsg != null) {
                    UserConfig.unban(qqMsg.fromId)
                    "qq[${qqMsg.fromId}] 已正常转发"
                } else "找不到该qq信息"
            } else {
                UserConfig.unban(user.id)
                "${user.firstName} 已正常转发"
            }
        } else {
            GroupConfig.unban(message.chat.id)
            return "群消息已正常转发"
        }
    }

}