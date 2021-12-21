package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class UnbindCommand(
    val cacheService: CacheService
) : AbstractCommand() {

    override val command = "unbind"
    override val help: String = "解绑群组或用户名"
    override val onlyGroupMessage = true

    override fun execute(update: Update, message: Message): String {
        val client = ContextHolder.telegramBotClient
        return if (message.isReply) {
            val user = message.replyToMessage.from
            if (user.userName == client.botUsername) {
                val qqMsg = cacheService.getQQByTg(message.replyToMessage)
                if (qqMsg != null) {
                    UserConfig.remove(qqMsg.fromId)
                    "qq[${qqMsg.fromId}] 解绑名称成功"
                } else "找不到该qq信息"
            } else {
                UserConfig.remove(user.id, user.userName)
                "${user.firstName} 解绑名称成功"
            }
        } else {
            GroupConfig.remove(message.chatId)
            "解绑Q群成功"
        }
    }
}