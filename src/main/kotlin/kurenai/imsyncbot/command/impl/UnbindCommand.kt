package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import org.springframework.stereotype.Component

@Component
class UnbindCommand(
    val cacheService: CacheService
) : AbstractTelegramCommand() {

    override val command = "unbind"
    override val help: String = "解绑群组或用户名"
    override val onlyAdmin = true
    override val onlySupperAdmin = false
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String {
        return if (message.isReply()) {
            val reply = message.replyToMessage!!
            val user = reply.from!!
            if (user.username == ContextHolder.telegramBot.username) {
                val qqMsg = cacheService.getQQByTg(reply)
                if (qqMsg != null) {
                    UserConfig.unbindUsername(qqMsg.fromId)
                    "qq[${qqMsg.fromId}] 解绑名称成功"
                } else "找不到该qq信息"
            } else {
                if (UserConfig.superAdmins.contains(message.from!!.id)) {
                    UserConfig.unbindUsername(user.id, user.username)
                    "${user.firstName} 解绑名称成功"
                } else {
                    "绑定群组操作需要超级管理员权限"
                }
            }
        } else {
            GroupConfig.remove(message.chat.id)
            "解绑Q群成功"
        }
    }
}