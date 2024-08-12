//package kurenai.imsyncbot.callback.impl
//
//import kurenai.imsyncbot.callback.Callback
//import kurenai.imsyncbot.getBotOrThrow
//import kurenai.imsyncbot.service.MessageService
//import kurenai.imsyncbot.telegram.send
//import moe.kurenai.tdlight.model.keyboard.InlineKeyboardButton
//import moe.kurenai.tdlight.model.keyboard.InlineKeyboardMarkup
//import moe.kurenai.tdlight.model.message.Message
//import moe.kurenai.tdlight.model.message.Update
//import moe.kurenai.tdlight.request.message.DeleteMessage
//import moe.kurenai.tdlight.request.message.EditMessageText
//import moe.kurenai.tdlight.util.getLogger
//
//class RetryCallback : Callback() {
//
//    private val log = getLogger()
//
//    override val method: String = "retry"
//
//    override suspend fun handle0(update: Update, message: Message): Int {
//
//        val originMessage = MessageService.getTg(message.chat.id, message.replyToMessage?.messageId!!)
//        if (originMessage == null) {
//            EditMessageText("转发失败：缓存中无法找到该条消息，无法重试").apply {
//                this.chatId = message.chatId
//                this.messageId = message.messageId
//            }.send()
//            return END
//        }
//
//        val messageId = message.messageId!!
//        val chatId = message.chatId
//        val retryMsg = "${message.text}\n\n正在重试..."
//        EditMessageText(retryMsg).apply {
//            this.chatId = chatId
//            this.messageId = messageId
//        }.send()
//
//        try {
//            getBotOrThrow().tgMessageHandler.onMessage(originMessage)
//            DeleteMessage(chatId, messageId).send()
//        } catch (e: Exception) {
//            log.error(e.message, e)
//            EditMessageText("#转发失败\n${e.message}").apply {
//                this.chatId = chatId
//                this.messageId = messageId
//                this.replyMarkup =
//                    InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("重试").apply { this.callbackData = "retry" })))
//            }.send()
//        }
//
//        return END
//    }
//}