package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.contact.getMemberOrFail
import net.mamoe.mirai.message.data.at

class ATCommand : AbstractTelegramCommand() {

    override val help = "AT qq 用户, 配合inline模式使用"
    override val command = "at"
    override val onlyGroupMessage = true

    override suspend fun execute(update: Update, message: Message): String? {
        val userId = message.text?.param()?.toLong() ?: return "参数错误"
        val bot = getBotOrThrow()
        val group = bot.getGroupFromMessage(message)
        group.sendMessage(group.getMemberOrFail(userId).at())
        return null
    }
}