package kurenai.imsyncbot.command

import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update


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
    open val parseMode: String? = null
    open val reply: Boolean = false

    abstract suspend fun execute(update: Update, message: Message): String?

    fun String.param(): String {
        return this.substring(command.length + 1).trim()
    }


}