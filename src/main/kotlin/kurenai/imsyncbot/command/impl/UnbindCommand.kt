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
class UnbindCommand(
    val cacheService: CacheService
) : AbstractTelegramCommand() {

    override val command = "unbind"
    override val help: String = "解绑群组或用户名"
    override val onlyAdmin = false
    override val onlySupperAdmin = true

    override fun execute(update: Update, message: Message): String {
        val param = message.text!!.param()
        return if (param.isNotBlank()) {
            try {
                GroupConfig.remove(param.toLong())
                "解绑Q群成功"
            } catch (e: Exception) {
                "参数错误"
            }
        } else {
            if (message.isReply()) {
                val reply = message.replyToMessage!!
                val user = reply.from!!
                if (user.username == ContextHolder.telegramBot.username) {
                    val qqMsg = cacheService.getQQByTg(reply)
                    if (qqMsg != null) {
                        UserConfig.unbindUsername(qqMsg.source.fromId)
                        "qq[${qqMsg.source.fromId}] 解绑名称成功"
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
}