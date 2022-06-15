package kurenai.imsyncbot.handler.qq

import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.message.data.MessageChain

interface QQHandler : Handler {

    suspend fun onGroupMessage(group: Group, messageChain: MessageChain): Int {
        return CONTINUE
    }

    suspend fun onRecall(event: MessageRecallEvent.GroupRecall): Int {
        return CONTINUE
    }

}