package kurenai.imsyncbot.command

import net.mamoe.mirai.event.events.MessageEvent

abstract class AbstractQQCommand {

    open val name: String = this::class.java.simpleName.replace("QQCommand", "")

    abstract suspend fun execute(event: MessageEvent): Int

}