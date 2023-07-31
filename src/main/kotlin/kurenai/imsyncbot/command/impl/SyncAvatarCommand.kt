package kurenai.imsyncbot.command.impl

import it.tdlight.jni.TdApi.*
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.utils.BotUtil
import kotlin.io.path.pathString

class SyncAvatarCommand : AbstractTelegramCommand() {

    override val command = "syncavatar"
    override val help: String = "同步群头像"
    override val onlyAdmin = true
    override val onlySupperAdmin = false
    override val onlyGroupMessage: Boolean = true

    override suspend fun execute(bot: ImSyncBot, message: Message, sender: MessageSenderUser, input: String): String? {
        val qqBot = bot.qq.qqBot
        var handled = false
        bot.userConfig.chatIdFriends[message.chatId]?.let { friendId ->
            qqBot.getFriend(friendId)?.let { friend ->
                val avatarPath = BotUtil.downloadImg("friend#$friendId.png", friend.avatarUrl, overwrite = true)
                bot.tg.execute {
                    SetChatPhoto(
                        message.chatId,
                        InputChatPhotoStatic(InputFileLocal(avatarPath.pathString))
                    )
                }
                handled = true
            }
        }
        bot.groupConfigService.tgQQ[message.chatId]?.let { groupId ->
            qqBot.getGroup(groupId)?.let {
                val avatarPath = BotUtil.downloadImg("group#$groupId.png", it.avatarUrl, overwrite = true)
                bot.tg.execute {
                    SetChatPhoto(
                        message.chatId,
                        InputChatPhotoStatic(InputFileLocal(avatarPath.pathString))
                    )
                }
                handled = true
            }
        }

        return if (handled) null
        else "找不到群或好友"
    }
}