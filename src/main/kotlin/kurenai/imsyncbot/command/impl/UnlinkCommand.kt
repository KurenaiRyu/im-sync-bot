package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source

class UnlinkCommand : AbstractTelegramCommand() {

    override val command = "unlink"
    override val help: String = "解除qq和tg的链接关系"
    override val onlyGroupMessage = true
    override val reply = true

    override suspend fun execute(update: Update, message: Message): String {
        val bot = getBotOrThrow()
        val user = if (message.isReply()) {
            if (!bot.userConfig.superAdmins.contains(message.from?.id)) return "只允许超级管理员管理他人信息"
            if (message.replyToMessage?.from?.username == bot.tg.username) {
                val qqMsg = CacheService.getQQByTg(message.replyToMessage!!) ?: return "找不到qq信息"
                val user = bot.userConfig.links.firstOrNull { it.qq == qqMsg.source.fromId } ?: return "该qq没有和tg建立链接关系"
                bot.userConfig.unlink(user)
                user
            } else {
                val user = bot.userConfig.links.firstOrNull { it.tg == message.replyToMessage?.from?.id }
                    ?: return "该用户没有和qq建立链接关系"
                bot.userConfig.unlink(user)
                user
            }
        } else {
            val user = bot.userConfig.links.firstOrNull { it.tg == message.from?.id } ?: return "未和qq建立链接关系"
            bot.userConfig.unlink(user)
            user
        }
        return "已取消qq[${user.qq}]和@${user.username}的链接关系"
    }
}