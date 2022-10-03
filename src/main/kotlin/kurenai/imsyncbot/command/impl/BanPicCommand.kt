package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source

class BanPicCommand : AbstractTelegramCommand() {

    override val command = "banPic"
    override val help: String = "排除群或用户图片消息"
    override val onlyGroupMessage = true

    override suspend fun execute(update: Update, message: Message): String {
        val bot = getBotOrThrow()
        return if (message.isReply()) {
            val replyMessage = message.replyToMessage!!
            val user = replyMessage.from!!
            if (user.isBot && user.username == bot.tg.username) {
                val qqMsg = CacheService.getQQByTg(replyMessage)
                if (qqMsg != null) {
                    bot.userConfig.banPic(qqMsg.source.fromId)
                    "qq[${qqMsg.source.fromId}] 已排除图片转发"
                } else "找不到该qq信息"
            } else {
                bot.userConfig.banPic(user.id)
                "${user.firstName} 已排除图片转发"
            }
        } else {
            bot.groupConfig.banPic(message.chat.id)
            "排除群图片信息设置成功"
        }
    }
}