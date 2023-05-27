package kurenai.imsyncbot.telegram

import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import moe.kurenai.tdlight.model.message.DeletedMessage
import moe.kurenai.tdlight.model.message.Message

interface TelegramHandler : Handler {

    suspend fun onMessage(message: Message): Int {
        return CONTINUE
    }

    suspend fun onEditMessage(message: Message): Int {
        return CONTINUE
    }

    suspend fun onDeleteMessage(deletedMessage: DeletedMessage): Int {
        return CONTINUE
    }

    @Throws(Exception::class)
    suspend fun onFriendMessage(message: Message): Int
}