package kurenai.imsyncbot.command

import it.tdlight.jni.TdApi.Message
import it.tdlight.jni.TdApi.MessageSenderUser
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.utils.ParseMode

abstract class AbstractTelegramCommand {

    open val help: String = "No help information."
    open val name: String = this.javaClass.simpleName.replace("Command", "")
    open val command: String = ""
    open val onlyUserMessage: Boolean = false
    open val onlyGroupMessage: Boolean = false
    open val onlyMaster: Boolean = false
    open val onlyAdmin: Boolean = false
    open val onlySupperAdmin: Boolean = true
    open val onlyReply: Boolean = false
    open val parseMode: ParseMode = ParseMode.TEXT
    open val reply: Boolean = false

    abstract suspend fun execute(bot: ImSyncBot, message: Message, sender: MessageSenderUser, input: String): String?

    fun String.param(): String {
        return this.substringAfter(' ').trim()
    }


}