package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source

class UnForwardCommand : AbstractTelegramCommand() {

    override val command = "unfwd"
    override val help: String = "排除群或用户消息（但事件仍会接受）"

    override suspend fun execute(update: Update, message: Message): String? {
        val bot = getBotOrThrow()
        return if (message.isReply()) {
            val reply = message.replyToMessage!!
            val user = reply.from!!
            if (user.isBot && user.username == bot.tg.username) {
                val qqMsg = CacheService.getQQByTg(reply)
                if (qqMsg != null) {
                    bot.userConfig.ban(qq = qqMsg.source.fromId)
                    "qq[${qqMsg.source.fromId}] 已排除转发"
                } else "找不到该qq信息"
            } else {
                bot.userConfig.ban(user.id)
                "${user.firstName} 已排除转发"
            }
        } else {
            bot.groupConfig.ban(message.chat.id)
            "排除群信息设置成功"
        }
    }
}