package kurenai.mybot.command

import net.mamoe.mirai.event.events.MessageEvent
import org.telegram.telegrambots.meta.api.objects.Update

interface Command {

    fun execute(update: Update): Boolean

    fun execute(event: MessageEvent): Boolean

    fun match(text: String): Boolean

    fun getHelp(): String

    fun getName(): String {
        return this.javaClass.simpleName
    }


}