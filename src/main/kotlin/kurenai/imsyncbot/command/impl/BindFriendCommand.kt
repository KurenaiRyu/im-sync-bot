package kurenai.imsyncbot.command.impl

import it.tdlight.jni.TdApi
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.service.FriendConfigService
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.contact.remarkOrNick


class BindFriendCommand : AbstractTelegramCommand() {
    private val log = getLogger()

    override val command = "bindFriend"
    override val help: String = "/bindFriend friendId 绑定群组为好友私聊"
    override val onlyAdmin = false
    override val onlySupperAdmin = true
    override val onlyGroupMessage: Boolean = true

    context(bot: ImSyncBot)
    override suspend fun execute(message: TdApi.Message, sender: TdApi.MessageSenderUser, input: String): String {
        val param = input.toLongOrNull() ?: return "参数错误"
        val qqBot = bot.qq.qqBot

        val friend = qqBot.getFriend(param) ?: return "找不到好友 $param"
        FriendConfigService.bindChat(friend, message.chatId)

        return "好友 ${friend.remarkOrNick}(${friend.id}) 私聊群绑定成功"
    }
}

class UnbindFriendCommand : AbstractTelegramCommand() {
    private val log = getLogger()

    override val command = "unbindFriend"
    override val help: String = "解绑好友私聊"
    override val onlyAdmin = false
    override val onlySupperAdmin = true

    context(bot: ImSyncBot)
    override suspend fun execute(message: TdApi.Message, sender: TdApi.MessageSenderUser, input: String): String {
        FriendConfigService.unbindChat(message.chatId)
        return "解绑好友私聊群成功"
    }
}