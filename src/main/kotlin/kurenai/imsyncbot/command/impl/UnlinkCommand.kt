package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class UnlinkCommand(
    val cacheService: CacheService
) : AbstractCommand() {

    override val command = "unlink"
    override val help: String = "解除qq和tg的链接关系"
    override val onlyGroupMessage = true
    override val reply = true

    override fun execute(update: Update, message: Message): String {
        val client = ContextHolder.telegramBotClient
        val user = if (message.isReply) {
            if (!UserConfig.superAdmins.contains(message.from.id)) return "只允许超级管理员管理他人信息"
            if (message.replyToMessage.from.userName == client.botUsername) {
                val qqMsg = cacheService.getQQByTg(message.replyToMessage) ?: return "找不到qq信息"
                val user = UserConfig.links.firstOrNull { it.qq == qqMsg.fromId } ?: return "该qq没有和tg建立链接关系"
                UserConfig.unlink(user)
                user
            } else {
                val user = UserConfig.links.firstOrNull { it.tg == message.replyToMessage.from.id } ?: return "该用户没有和qq建立链接关系"
                UserConfig.unlink(user)
                user
            }
        } else {
            val user = UserConfig.links.firstOrNull { it.tg == message.from.id } ?: return "未和qq建立链接关系"
            UserConfig.unlink(user)
            user
        }
        return "已取消qq[${user.qq}]和@${user.username}的链接关系"
    }
}