package kurenai.imsyncbot.handler.tg

import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import moe.kurenai.tdlight.model.message.Message

interface TelegramHandler : Handler {

    suspend fun onMessage(message: Message): Int {
        return CONTINUE
    }

    suspend fun onEditMessage(message: Message): Int {
        return CONTINUE
    }


}