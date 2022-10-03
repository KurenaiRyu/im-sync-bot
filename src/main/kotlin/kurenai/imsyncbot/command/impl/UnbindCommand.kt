package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source

class UnbindCommand : AbstractTelegramCommand() {

    override val command = "unbind"
    override val help: String = "解绑群组或用户名"
    override val onlyAdmin = false
    override val onlySupperAdmin = true

    override suspend fun execute(update: Update, message: Message): String {
        val bot = getBotOrThrow()
        val param = message.text!!.param()
        return if (param.isNotBlank()) {
            try {
                bot.groupConfig.remove(param.toLong())
                "解绑Q群成功"
            } catch (e: Exception) {
                "参数错误"
            }
        } else {
            if (message.isReply()) {
                val reply = message.replyToMessage!!
                val user = reply.from!!
                if (user.username == bot.tg.username) {
                    val qqMsg = CacheService.getQQByTg(reply)
                    if (qqMsg != null) {
                        bot.userConfig.unbindUsername(qqMsg.source.fromId)
                        "qq[${qqMsg.source.fromId}] 解绑名称成功"
                    } else "找不到该qq信息"
                } else {
                    if (bot.userConfig.superAdmins.contains(message.from!!.id)) {
                        bot.userConfig.unbindUsername(user.id, user.username)
                        "${user.firstName} 解绑名称成功"
                    } else {
                        "绑定群组操作需要超级管理员权限"
                    }
                }
            } else {
                bot.groupConfig.remove(message.chat.id)
                "解绑Q群成功"
            }
        }
    }
}