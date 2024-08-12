//package kurenai.imsyncbot.command.impl
//
//import it.tdlight.jni.TdApi
//import kurenai.imsyncbot.command.AbstractTelegramCommand
//import kurenai.imsyncbot.getBotOrThrow
//import moe.kurenai.tdlight.model.message.Message
//
//class AdminCommand : AbstractTelegramCommand() {
//
//    override val command = "admin"
//    override val help: String = "设置管理员"
//    override val onlyGroupMessage = true
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String {
//        return if (message.isReply()) {
//            val user = message.replyToMessage!!.from!!
//            if (user.isBot) "机器人不能够是管理员"
//            else {
//                getBotOrThrow().userConfig.admin(user.id, username = user.username)
//                "添加管理员成功"
//            }
//        } else {
//            "需要引用一条消息来找到该用户"
//        }
//    }
//
//}
//
//class RemoveAdminCommand : AbstractTelegramCommand() {
//
//    override val command = "removeAdmin"
//    override val help: String = "移除管理员"
//    override val onlyGroupMessage = true
//    override val onlyReply = true
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String {
//        val reply = message.replyToMessage!!
//        val user = reply.from!!
//        getBotOrThrow().userConfig.removeAdmin(user.id)
//        return "移除管理员成功"
//    }
//
//}