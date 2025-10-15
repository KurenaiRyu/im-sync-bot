package kurenai.imsyncbot.bot.qq

import kotlinx.coroutines.CoroutineScope
import kurenai.imsyncbot.BotProperties
import kurenai.imsyncbot.domain.QQMessage
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.message.data.OnlineShortVideo

class PrivateMessageContext(
    entity: QQMessage?,
    val parentScope: CoroutineScope,
    val properties: BotProperties,
    val event: FriendMessageEvent
): MessageContext(entity, parentScope, properties) {



    fun handle() {
        event.message.map { msg ->
            when (msg) {
                is OnlineShortVideo -> {}
            }
        }
    }

}