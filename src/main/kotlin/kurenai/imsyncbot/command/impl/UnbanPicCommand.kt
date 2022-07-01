package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBot
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source

class UnbanPicCommand : AbstractTelegramCommand() {

    override val command = "unbanPic"
    override val help: String = "解除排除群或用户图片消息"
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String {
        return if (message.isReply()) {
            val reply = message.replyToMessage!!
            val user = reply.from!!
            if (user.isBot && user.username == TelegramBot.username) {
                val qqMsg = CacheService.getQQByTg(reply)
                if (qqMsg != null) {
                    UserConfig.unbanPic(qqMsg.source.fromId)
                    "qq[${qqMsg.source.fromId}] 已正常转发"
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