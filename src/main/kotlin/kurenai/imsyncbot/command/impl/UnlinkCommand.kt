package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source
import org.springframework.stereotype.Component

@Component
class UnlinkCommand(
    val cacheService: CacheService
) : AbstractTelegramCommand() {

    override val command = "unlink"
    override val help: String = "解除qq和tg的链接关系"
    override val onlyGroupMessage = true
    override val reply = true

    override fun execute(update: Update, message: Message): String {
        val user = if (message.isReply()) {
            if (!UserConfig.superAdmins.contains(message.from?.id)) return "只允许超级管理员管理他人信息"
            if (message.replyToMessage?.from?.username == ContextHolder.telegramBot.username) {
                val qqMsg = cacheService.getQQByTg(message.replyToMessage!!) ?: return "找不到qq信息"
                val user = UserConfig.links.firstOrNull { it.qq == qqMsg.source.fromId } ?: return "该qq没有和tg建立链接关系"
                UserConfig.unlink(user)
                user
            } else {
                val user = UserConfig.links.firstOrNull { it.tg == message.replyToMessage?.from?.id } ?: return "该用户没有和qq建立链接关系"
                UserConfig.unlink(user)
                user
            }
        } else {
            val user = UserConfig.links.firstOrNull { it.tg == message.from?.id } ?: return "未和qq建立链接关系"
            UserConfig.unlink(user)
            user
        }
        return "已取消qq[${user.qq}]和@${user.username}的链接关系"
    }
}