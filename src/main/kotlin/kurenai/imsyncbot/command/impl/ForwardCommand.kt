//package kurenai.imsyncbot.command.impl
//
//import it.tdlight.jni.TdApi
//import kurenai.imsyncbot.command.AbstractTelegramCommand
//import kurenai.imsyncbot.getBotOrThrow
//import kurenai.imsyncbot.service.CacheService
//import moe.kurenai.tdlight.model.message.Message
//import net.mamoe.mirai.message.data.source
//
//class ForwardCommand : AbstractTelegramCommand() {
//
//    override val command = "fwd"
//    override val help: String = "解除排除群或用户消息"
//    override val onlyGroupMessage = true
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String {
//        val bot = getBotOrThrow()
//        return if (message.isReply()) {
//            val user = message.replyToMessage!!.from!!
//            if (user.isBot && user.username == bot.tg.username) {
//                val qqMsg = CacheService.getQQByTg(message.replyToMessage!!)
//                if (qqMsg != null) {
//                    bot.userConfig.unban(qqMsg.source.fromId)
//                    "qq[${qqMsg.source.fromId}] 已正常转发"
//                } else "找不到该qq信息"
//            } else {
//                bot.userConfig.unban(user.id)
//                "${user.firstName} 已正常转发"
//            }
//        } else {
//            bot.groupConfig.unban(message.chat.id)
//            return "群消息已正常转发"
//        }
//    }
//
//}
//
//class UnForwardCommand : AbstractTelegramCommand() {
//
//    override val command = "unfwd"
//    override val help: String = "排除群或用户消息（但事件仍会接受）"
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String? {
//        val bot = getBotOrThrow()
//        return if (message.isReply()) {
//            val reply = message.replyToMessage!!
//            val user = reply.from!!
//            if (user.isBot && user.username == bot.tg.username) {
//                val qqMsg = CacheService.getQQByTg(reply)
//                if (qqMsg != null) {
//                    bot.userConfig.ban(qq = qqMsg.source.fromId)
//                    "qq[${qqMsg.source.fromId}] 已排除转发"
//                } else "找不到该qq信息"
//            } else {
//                bot.userConfig.ban(user.id)
//                "${user.firstName} 已排除转发"
//            }
//        } else {
//            bot.groupConfig.ban(message.chat.id)
//            "排除群信息设置成功"
//        }
//    }
//}