package kurenai.imsyncbot.command.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import it.tdlight.jni.TdApi.*
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.utils.BotUtil


@ContributesIntoSet(AppScope::class)
class SyncAvatarCommand : AbstractTelegramCommand() {

    override val command = "syncavatar"
    override val help: String = "同步群头像"
    override val onlyAdmin = true
    override val onlySupperAdmin = false
    override val onlyGroupMessage: Boolean = true

    context(bot: ImSyncBot)
    override suspend fun execute(message: Message, sender: MessageSenderUser, input: String): String? {
        val qqBot = bot.qq.qqBot
        var handled = false
        bot.userConfigService.chatIdFriends[message.chatId]?.let { friendId ->
            qqBot.getFriend(friendId)?.let { friend ->
                val avatarPath = BotUtil.downloadImg("friend#$friendId.png", friend.avatarUrl, overwrite = true)
                bot.tg.send {
                    SetChatPhoto(
                        message.chatId,
                        InputChatPhotoStatic(InputFileLocal(avatarPath.toString()))
                    )
                }
                handled = true
            }
        }
        bot.groupConfigService.findByTg(message.chatId)?.qqGroupId?.let { groupId ->
            qqBot.getGroup(groupId)?.let {
                val avatarPath = BotUtil.downloadImg("group#$groupId.png", it.avatarUrl, overwrite = true)
                bot.tg.send {
                    SetChatPhoto(
                        message.chatId,
                        InputChatPhotoStatic(InputFileLocal(avatarPath.toString()))
                    )
                }
                handled = true
            }
        }

        return if (handled) null
        else "找不到群或好友"
    }
}