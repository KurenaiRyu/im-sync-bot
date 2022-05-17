package kurenai.imsyncbot

import kurenai.imsyncbot.command.AbstractQQCommand
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.command.DelegatingCommand
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MyBotApplication

fun main(args: Array<String>) {
    val context = runApplication<MyBotApplication>(*args)
    context.getBeansOfType(AbstractTelegramCommand::class.java).values.forEach(DelegatingCommand::addTgHandle)
    context.getBeansOfType(AbstractQQCommand::class.java).values.forEach(DelegatingCommand::addQQHandle)
}