package kurenai.imsyncbot.bot.qq

import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import net.mamoe.mirai.event.events.MessageRecallEvent

interface QQHandler : Handler {

    suspend fun onGroupMessage(context: GroupMessageContext): Int {
        return CONTINUE
    }

//    suspend fun onFriendMessage(context: PrivateMessageContext): Int {
//        return CONTINUE
//    }

    suspend fun onRecall(event: MessageRecallEvent.GroupRecall): Int {
        return CONTINUE
    }

}