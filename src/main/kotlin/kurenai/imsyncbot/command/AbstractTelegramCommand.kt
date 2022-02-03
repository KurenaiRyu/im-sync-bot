package kurenai.imsyncbot.command

import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update


abstract class AbstractTelegramCommand {

    open val help: String = "No help information."
    open val name: String = this.javaClass.simpleName
    open val command: String = ""
    open val onlyUserMessage: Boolean = false
    open val onlyGroupMessage: Boolean = false
    open val onlyMaster: Boolean = false
    open val onlyAdmin: Boolean = false
    open val onlySupperAdmin: Boolean = true
    open val onlyReply: Boolean = false
    open val parseMode: String? = null
    open val reply: Boolean = false

    init {
        DelegatingCommand.addTgHandle(this)
    }

    abstract fun execute(update: Update, message: Message): String?

    fun String.body(): String {
        return this.replace("/$command", "")
    }


}