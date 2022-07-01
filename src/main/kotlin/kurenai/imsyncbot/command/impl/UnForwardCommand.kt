package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBot
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source

class UnForwardCommand : AbstractTelegramCommand() {

    override val command = "unfwd"
    override val help: String = "排除群或用户消息（但事件仍会接受）"
    override val onlyGroupMessage = true

    override suspend fun execute(update: Update, message: Message): String? {
        return if (message.isReply()) {
            val reply = message.replyToMessage!!
            val user = reply.from!!
            if (user.isBot && user.username == TelegramBot.username) {
                val qqMsg = CacheService.getQQByTg(reply)
                if (qqMsg != null) {
                    UserConfig.ban(qq = qqMsg.source.fromId)
                    "qq[${qqMsg.source.fromId}] 已排除转发"
                } else "找不到该qq信息"
            } else {
                UserConfig.ban(user.id)
                "${user.firstName} 已排除转发"
            }
        } else {
            GroupConfig.ban(message.chat.id)
            "排除群信息设置成功"
        }
    }
}