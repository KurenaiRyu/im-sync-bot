package kurenai.imsyncbot.bot.qq

import it.tdlight.jni.TdApi
import kotlinx.coroutines.CoroutineScope
import kurenai.imsyncbot.BotProperties
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.domain.QQMessage
import kurenai.imsyncbot.service.FileService
import kurenai.imsyncbot.service.FriendConfigService
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.asFmtText
import net.mamoe.mirai.contact.remarkOrNick
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.ImageType
import net.mamoe.mirai.message.data.content

class PrivateMessageContext(
    entity: QQMessage?,
    parentScope: CoroutineScope,
    properties: BotProperties,
    val event: FriendMessageEvent
): MessageContext(entity, parentScope, properties) {

    var replyMessage: TdApi.Message? = null
    var contents: List<TdApi.InputMessageContent> = emptyList()

    context(bot: ImSyncBot)
    suspend fun handle(): List<TdApi.InputMessageContent> {
        replyMessage = MessageService.findTgMessageByQQQuote(event.message)
        contents = event.message.map { msg ->
            when (msg) {
                is Image -> {
                    val imageType = msg.imageType
                    if (ImageType.GIF == imageType ||
                        ImageType.APNG == imageType ||
                        (ImageType.UNKNOWN == imageType && msg.isEmoji)
                    ) {
                        val (id, url, inputFile, type) = FileService.download(msg, "gif")
                        TdApi.InputMessageAnimation().apply {
                            this.animation = inputFile
                        }
                    } else {
                        val (id, url, inputFile, type) = FileService.download(msg.queryUrl())
                        TdApi.InputMessagePhoto().apply {
                            this.photo = inputFile
                        }
                    }
                }

                else -> {
                    TdApi.InputMessageText().apply {
                        this.text = msg.content.asFmtText()
                    }
                }
            }
        }
        return contents
    }

}