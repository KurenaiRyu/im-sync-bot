package kurenai.imsyncbot.command

import net.mamoe.mirai.event.events.MessageEvent

abstract class AbstractQQCommand {

    abstract suspend fun execute(event: MessageEvent): Int

}