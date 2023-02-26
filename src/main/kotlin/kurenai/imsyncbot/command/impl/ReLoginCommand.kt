package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBot
import kurenai.imsyncbot.getBotOrThrow
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update

class ReLoginCommand : AbstractTelegramCommand() {

    override val command = "relogin"
    override val help: String = "qq若掉线则重新登陆"
    override val onlySupperAdmin = false

    override suspend fun execute(update: Update, message: Message): String {
        val isOnline = getBot()?.qq?.qqBot?.isOnline ?: false
        val msg = if (isOnline) {
            "在线中，无需重登录"
        } else {
            kotlin.runCatching {
                getBotOrThrow().qq.restart()
                "重登录成功"
            }.recover {
                "重登录失败: ${it.message}"
            }.getOrThrow()
        }
        return msg
    }

}