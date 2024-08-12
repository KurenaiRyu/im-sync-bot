//package kurenai.imsyncbot.command.impl
//
//import it.tdlight.jni.TdApi
//import kurenai.imsyncbot.command.AbstractTelegramCommand
//import kurenai.imsyncbot.getBotOrThrow
//import kurenai.imsyncbot.service.CacheService
//import kurenai.imsyncbot.telegram.send
//import moe.kurenai.tdlight.model.message.Message
//import moe.kurenai.tdlight.request.message.DeleteMessage
//import moe.kurenai.tdlight.request.message.SendMessage
//import moe.kurenai.tdlight.util.getLogger
//import net.mamoe.mirai.contact.recallMessage
//import net.mamoe.mirai.message.data.source
//
//class RecallCommand : AbstractTelegramCommand() {
//
//    private val log = getLogger()
//    override val help: String = "撤回消息"
//    override val command: String = "recall"
//    override val onlySupperAdmin: Boolean = false
//    override val onlyReply: Boolean = true
//    override val reply: Boolean = true
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String? {
//        val replyMsg = message.replyToMessage!!
//        return try {
//            val qqMsg = CacheService.getQQByTg(replyMsg)
//            if (qqMsg == null)
//                SendMessage(message.chatId, "未能找到对应的qq消息").send()
//            else {
//                getBotOrThrow().qq.qqBot.getGroup(qqMsg.source.targetId)?.recallMessage(qqMsg)
//                if (!DeleteMessage(message.chatId, replyMsg.messageId!!).send()) {
//                    SendMessage("已删除", message.chatId).send()
//                } else {
//                    DeleteMessage(message.chatId, message.messageId!!).send()
//                }
//            }
//            null
//        } catch (e: Exception) {
//            log.error(e.message, e)
//            "error: ${e.message}"
//        }
//    }
//}