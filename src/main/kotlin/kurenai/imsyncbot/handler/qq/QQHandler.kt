package kurenai.imsyncbot.handler.qq

import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.MessageRecallEvent

interface QQHandler : Handler {

    suspend fun onGroupMessage(event: GroupAwareMessageEvent): Int {
        return CONTINUE
    }

    suspend fun onRecall(event: MessageRecallEvent): Int {
        return CONTINUE
    }

}