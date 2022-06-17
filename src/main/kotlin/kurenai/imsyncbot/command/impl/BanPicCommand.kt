package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source
import org.springframework.stereotype.Component

@Component
class BanPicCommand(
    val cacheService: CacheService,
) : AbstractTelegramCommand() {

    override val command = "banPic"
    override val help: String = "排除群或用户图片消息"
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String? {
        return if (message.isReply()) {
            val replyMessage = message.replyToMessage!!
            val user = replyMessage.from!!
            if (user.isBot && user.username == ContextHolder.telegramBot.username) {
                val qqMsg = cacheService.getQQByTg(replyMessage)
                if (qqMsg != null) {
                    UserConfig.banPic(qqMsg.source.fromId)
                    "qq[${qqMsg.source.fromId}] 已排除图片转发"
                } else "找不到该qq信息"
            } else {
                UserConfig.banPic(user.id)
                "${user.firstName} 已排除图片转发"
            }
        } else {
            GroupConfig.banPic(message.chat.id)
            "排除群图片信息设置成功"
        }
    }
}