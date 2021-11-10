package kurenai.imsyncbot.command

import net.mamoe.mirai.event.events.MessageEvent
import org.telegram.telegrambots.meta.api.objects.Update

abstract class Command {

    open val help: String = "No help information."
    open val name: String = this.javaClass.simpleName
    open val command: String = ""
    open val onlyUserMessage: Boolean = false

    open fun execute(update: Update): Boolean {
        return false
    }

    open fun execute(event: MessageEvent): Boolean {
        return false
    }

    open fun match(update: Update): Boolean {
        return match(update.message.text)
    }

    open fun match(text: String): Boolean {
        val commandText = text.substringBefore(" ").substring(1)
        return commandText == command
    }

    fun getBody(text: String): String = text.substringAfter(" ")


}