package kurenai.imsyncbot.command

import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

abstract class AbstractCommand {

    open val help: String = "No help information."
    open val name: String = this.javaClass.simpleName
    open val command: String = ""
    open val onlyUserMessage: Boolean = false
    open val onlyGroupMessage: Boolean = false
    open val onlyMaster: Boolean = false
    open val onlyAdmin: Boolean = true
    open val onlySupperAdmin: Boolean = false
    open val onlyReply: Boolean = false
    open val parseMode: String? = null
    open val reply: Boolean = false

    init {
        DelegatingCommand.add(this)
    }

    abstract fun execute(update: Update, message: Message): String?

    fun String.body(): String {
        return this.replace("/$command", "")
    }


}