package kurenai.mybot.command.impl

import kurenai.mybot.command.Command
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class ErrorCommand : Command {
    override fun match(text: String): Boolean {
        return text.startsWith("/error")

    }

    override fun execute(update: Update): Boolean {
        println(update)
        if (true) {
            throw Exception("test")
        }
        return false
    }
}