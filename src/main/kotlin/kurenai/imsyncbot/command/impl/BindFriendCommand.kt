//package kurenai.imsyncbot.command.impl
//
//import it.tdlight.jni.TdApi
//import kurenai.imsyncbot.command.AbstractTelegramCommand
//import kurenai.imsyncbot.getBotOrThrow
//import moe.kurenai.tdlight.model.message.Message
//import moe.kurenai.tdlight.util.getLogger
//import net.mamoe.mirai.contact.remarkOrNick
//
//private val log = getLogger()
//
//class BindFriendCommand : AbstractTelegramCommand() {
//
//    override val command = "bindFriend"
//    override val help: String = "/bindFriend friendId 绑定群组为好友私聊"
//    override val onlyAdmin = false
//    override val onlySupperAdmin = true
//    override val onlyGroupMessage: Boolean = true
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String {
//        val param = message.text?.param()?.toLongOrNull() ?: return "参数错误"
//        val bot = getBotOrThrow()
//        val qqBot = bot.qq.qqBot
//
//        val friend = qqBot.getFriend(param) ?: return "找不到好友 $param"
//
//        bot.userConfig.bindChat(friend.id, message.chat.id)
//
//        return "好友 ${friend.remarkOrNick}(${friend.id}) 私聊群绑定成功"
//    }
//}
//
//class UnbindFriendCommand : AbstractTelegramCommand() {
//
//    override val command = "unbindFriend"
//    override val help: String = "解绑好友私聊"
//    override val onlyAdmin = false
//    override val onlySupperAdmin = true
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String {
//        val bot = getBotOrThrow()
//        bot.userConfig.unbindChat(message.chat.id)
//        return "解绑Q群成功"
//    }
//}