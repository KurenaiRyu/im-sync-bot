package kurenai.imsyncbot.entity

import net.mamoe.mirai.message.data.MessageSourceKind
import net.mamoe.mirai.message.data.OnlineMessageSource
import net.mamoe.mirai.message.data.kind

class MessageSourceCache {

    lateinit var ids: IntArray
    lateinit var internalIds: IntArray
    var time: Int = 0
    var fromId: Long = 0L
    var targetId: Long = 0L
    var botId: Long = 0L
    lateinit var content: String
    lateinit var kind: MessageSourceKind

    constructor()

    constructor(messageSource: OnlineMessageSource) {
        ids = messageSource.ids
        internalIds = messageSource.internalIds
        time = messageSource.time
        fromId = messageSource.fromId
        targetId = messageSource.targetId
        botId = messageSource.botId
        content = messageSource.originalMessage.contentToString().takeIf { it.isNotEmpty() } ?: " "
        kind = messageSource.kind
    }


}
