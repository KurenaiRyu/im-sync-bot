package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.BotUtil
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.SendDocument

class GroupCfgCommand : AbstractTelegramCommand() {

    override val command = "groupcfg"
    override val help: String = "获取群配置/更新群配置(引用文件)"
    override val onlySupperAdmin = true
    override val onlyUserMessage = true

    override suspend fun execute(update: Update, message: Message): String? {
        val bot = getBotOrThrow()
        return if (message.isReply()) {
            message.replyToMessage?.document?.let { doc ->
                val file = BotUtil.downloadTgFile(doc.fileId, doc.fileUniqueId)
                bot.groupConfig.load(file)
                "配置已更新"
            } ?: "无效引用"
        } else {
            bot.groupConfig.save()
            SendDocument(message.chatId, InputFile(bot.groupConfig.file)).send()
            null
        }
    }
}