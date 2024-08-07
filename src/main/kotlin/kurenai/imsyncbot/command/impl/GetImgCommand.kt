package kurenai.imsyncbot.command.impl

import it.tdlight.jni.TdApi
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.command.AbstractTelegramCommand

class GetImgCommand : AbstractTelegramCommand() {

    override val command = "getimg"
    override val help: String = "获取消息中的媒体以文件发送"
    override val onlyGroupMessage = true
    override val onlyReply = true

    override suspend fun execute(
        bot: ImSyncBot,
        message: TdApi.Message,
        sender: TdApi.MessageSenderUser,
        input: String
    ): String? {
//        val replyToMsg = (message.replyTo as? MessageReplyToMessage) ?: return "必须引用一条消息"
//        val messageSource = MessageService.findQQByTg(replyToMsg.chatId, replyToMsg.messageId)
//        if (messageSource != null) {
//            val images = messageSource.filterIsInstance<Image>()
//            val inputFileFlow = FileService.download(images)
//            kotlin.runCatching {
//                if (images.size == 1) {
//                    bot.tg.send(SendMessage().apply {
//                        this.chatId = message.chatId
//                        this.inputMessageContent = InputMessageDocument().apply {
//                            this.document = inputFileFlow.first()
//                        }
//                        this.replyTo = messageReplayToMessage(message)
//                    })
//                } else if (images.size in 1..10) {
//                    bot.tg.send(SendMessageAlbum().apply {
//                        this.chatId = message.chatId
//                        this.inputMessageContents = inputFileFlow.map { inputFile ->
//                            InputMessageDocument().apply {
//                                this.document = inputFile
//                            }
//                        }.toList().toTypedArray()
//                        this.replyTo = messageReplayToMessage(message)
//                    })
//                } else if (images.size > 10) {
//                    inputFileFlow
//                        .map { inputMessageDocument(message.chatId, it) }
//                        .toList()
//                        .windowed(10)
//                        .map { documents ->
//                            bot.tg.send(SendMessageAlbum().apply {
//                                this.chatId = message.chatId
//                                this.inputMessageContents = documents.toTypedArray()
//                            })
//                        }
//
//                } else null
//            }.recover {
//                bot.tg.sendMessageText(images.map { it.queryUrl() }.toTypedArray().joinToString("\n"), message.chatId)
//            }
//        } else return "找不到该qq消息媒体"
        return null
    }

}