package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.contact.PermissionDeniedException
import net.mamoe.mirai.contact.isAdministrator

class QQAdminCommand : AbstractTelegramCommand() {

    override val help = "设置该qq的管理员，重复执行会进行撤销与提升"
    override val command = "qqadmin"
    override val onlySupperAdmin = true
    override val onlyGroupMessage = true

    override suspend fun execute(update: Update, message: Message): String {
        val member = getBotOrThrow().getMemberFromMessage(message)
        return kotlin.runCatching {
            if (member.isAdministrator()) {
                member.modifyAdmin(false)
                "已撤销[${member.id}]的管理员"
            } else {
                member.modifyAdmin(true)
                "已提升[${member.id}]为管理员"
            }
        }.recover {
            if (it is PermissionDeniedException) "Bot无权限修改管理员"
            else throw it
        }.getOrThrow()
    }
}