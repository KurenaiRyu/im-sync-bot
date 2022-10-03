package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source

class ForwardCommand : AbstractTelegramCommand() {

    override val command = "fwd"
    override val help: String = "解除排除群或用户消息"
    override val onlyGroupMessage = true

    override suspend fun execute(update: Update, message: Message): String {
        val bot = getBotOrThrow()
        return if (message.isReply()) {
            val user = message.replyToMessage!!.from!!
            if (user.isBot && user.username == bot.tg.username) {
                val qqMsg = CacheService.getQQByTg(message.replyToMessage!!)
                if (qqMsg != null) {
                    bot.userConfig.unban(qqMsg.source.fromId)
                    "qq[${qqMsg.source.fromId}] 已正常转发"
                } else "找不到该qq信息"
            } else {
                bot.userConfig.unban(user.id)
                "${user.firstName} 已正常转发"
            }
        } else {
            bot.groupConfig.unban(message.chat.id)
            return "群消息已正常转发"
        }
    }

}