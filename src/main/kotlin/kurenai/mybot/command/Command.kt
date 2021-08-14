package kurenai.mybot.command

import net.mamoe.mirai.event.events.MessageEvent
import org.telegram.telegrambots.meta.api.objects.Update

interface Command {

    fun execute(update: Update): Boolean {
        return true
    }

    fun execute(event: MessageEvent): Boolean {
        return true
    }

    fun match(text: String): Boolean

    fun getHelp(): String {
        return "No help information."
    }

    fun getName(): String {
        return this.javaClass.simpleName
    }


}