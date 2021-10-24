package kurenai.imsyncbot.command

import net.mamoe.mirai.event.events.MessageEvent
import org.telegram.telegrambots.meta.api.objects.Update

interface Command {

    suspend fun execute(update: Update): Boolean {
        return true
    }

    suspend fun execute(event: MessageEvent): Boolean {
        return true
    }

    fun match(update: Update): Boolean {
        return match(update.message.text)
    }

    fun match(text: String): Boolean

    fun getHelp(): String {
        return "No help information."
    }

    fun getName(): String {
        return this.javaClass.simpleName
    }


}