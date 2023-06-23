package kurenai.imsyncbot.command.impl

import it.tdlight.jni.TdApi
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.command.AbstractTelegramCommand

class DefaultCommand : AbstractTelegramCommand() {

    override val command = "default"
    override val help: String = "设置默认群"
    override val onlyGroupMessage = true

    override suspend fun execute(
        bot: ImSyncBot,
        message: TdApi.Message,
        sender: TdApi.MessageSenderUser,
        input: String
    ): String {
        return if (bot.groupConfig.default(message))
            "设置默认群成功"
        else
            "已撤销当前群作为默认群"
    }

}