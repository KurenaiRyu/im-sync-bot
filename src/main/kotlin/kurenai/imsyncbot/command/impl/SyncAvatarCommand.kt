package kurenai.imsyncbot.command.impl

import io.ktor.client.*
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.HttpUtil
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.chat.SetChatPhoto
import moe.kurenai.tdlight.util.getLogger
import java.nio.file.Path

class SyncAvatarCommand : AbstractTelegramCommand() {

    override val command = "syncavatar"
    override val help: String = "同步群头像"
    override val onlyAdmin = true
    override val onlySupperAdmin = false
    override val onlyGroupMessage: Boolean = true

    override suspend fun execute(update: Update, message: Message): String? {
        val bot = getBotOrThrow()
        val qqBot = bot.qq.qqBot
        val chatId = message.chat.id
        var handled = false
        bot.userConfig.chatIdFriends[chatId]?.let { friendId ->
            qqBot.getFriend(friendId)?.let {
                val avatarPath = BotUtil.downloadImg("$friendId.png", it.avatarUrl)
                SetChatPhoto(message.chatId, InputFile(avatarPath.toFile()).apply {
                    fileName = "$friendId.png"
                    isNew = true
                }).send(bot.tg)
                handled = true
            }
        }
        bot.groupConfig.tgQQ[chatId]?.let { groupId ->
            qqBot.getGroup(groupId)?.let {
                val avatarPath = BotUtil.downloadImg("$groupId.png", it.avatarUrl)
                SetChatPhoto(message.chatId, InputFile(avatarPath.toFile()).apply {
                    fileName = "$groupId.png"
                    isNew = true
                }).send(bot.tg)
                handled = true
            }
        }

        return if (handled) null
        else "找不到群或好友"
    }
}