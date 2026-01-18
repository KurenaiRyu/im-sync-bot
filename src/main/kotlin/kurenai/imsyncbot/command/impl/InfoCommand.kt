package kurenai.imsyncbot.command.impl

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.UserTypeBot
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.dfs
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.service.FriendConfigService
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.telegram.*
import kurenai.imsyncbot.utils.withVT
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.message.data.sourceOrNull
import java.time.Instant
import java.time.ZoneId
import kotlin.io.path.pathString

class InfoCommand : AbstractTelegramCommand() {

    override val command = "info"
    override val help: String = "查看用户或群信息"
    override val onlyGroupMessage = true
    override val parseMode: ParseMode = ParseMode.MARKDOWN_V2

    context(bot: ImSyncBot)
    override suspend fun execute(
        message: TdApi.Message,
        sender: TdApi.MessageSenderUser,
        input: String
    ): String? {
        val qqBot = bot.qq.qqBot
        val replyToMessageId = message.replyTo?.messageId ?: 0
        return if (replyToMessageId > 0) {
            val replyMessage = bot.tg.getMessage(message.chatId, replyToMessageId)
            if (sender.userId == bot.tg.getMe().id) {
                val qqMsgSource = MessageService.findQQByTg(replyMessage)?.sourceOrNull
                if (qqMsgSource != null) {
                    val userId = qqMsgSource.fromId
                    val qqGroup = bot.groupConfigService.findByTg(message.chatId)?.qqGroupId?.let { qqBot.getGroup(it) }
                    if (qqGroup == null) {
                        return "找不到绑定的qq群"
                    } else {
                        val config = bot.userConfigService.configs.firstOrNull { it.qq == userId }
                        val member = qqGroup[userId]
                        if (member != null) {

                            val list = ArrayList<String>()
                            list.add("id: `${member.id}`")
                            list.add("昵称: `${member.nick.escapeMarkdown()}`")
                            if (member.remark.isNotBlank())
                                list.add("备注: `${member.nameCard.escapeMarkdown()}`")
                            if (member.nameCard.isNotBlank())
                                list.add("名片: `${member.nameCard.escapeMarkdown()}`")
                            if (member.specialTitle.isNotBlank())
                                list.add("头衔: `${member.specialTitle.escapeMarkdown()}`")
                            list.add("身份: ${member.permission}")
                            if (config?.bindingName?.isNotBlank() == true)
                                list.add("绑定名称: `${config.bindingName!!.escapeMarkdown()}`")
                            if (member.isMuted)
                                list.add("被禁言${member.muteTimeRemaining}s")
                            list.add(
                                "入群时间: `${
                                    Instant.ofEpochSecond(member.joinTimestamp.toLong()).atZone(ZoneId.systemDefault())
                                        .format(dfs).escapeMarkdown()
                                }`"
                            )
                            list.add(
                                "最后发言: `${
                                    Instant.ofEpochSecond(member.lastSpeakTimestamp.toLong())
                                        .atZone(ZoneId.systemDefault()).format(dfs).escapeMarkdown()
                                }`"
                            )
                            if (config?.status?.isNotEmpty() == true)
                                list.add("状态: ${config.status.toString().escapeMarkdown()}")

                            val path = withVT {
                                BotUtil.downloadImg(
                                    "user-avatar-${member.id}.png",
                                    member.avatarUrl,
                                    overwrite = true
                                )
                            }
                            bot.tg.send {
                                messagePhoto(
                                    message.chatId,
                                    path.pathString,
                                    list.joinToString("\n").fmt(ParseMode.MARKDOWN_V2)
                                )
                            }
                            null
                        } else {
                            "找不到qq账户$userId"
                        }
                    }
                } else "找不到该qq信息"
            } else {
                val list = ArrayList<String>()
                val user = bot.tg.getUser(sender.userId)
                list.add("id: `${sender.userId}`")
                list.add("username: `${user.username().escapeMarkdown()}`")
                list.add("firstName: `${user.firstName.escapeMarkdown()}`")
                list.add("lastName: `${user.lastName?.escapeMarkdown()}`")
                list.add("isBot: ${user.type.constructor == UserTypeBot.CONSTRUCTOR}")
                bot.userConfigService.configs.firstOrNull {
                    it.tg == sender.userId
                }?.let {
                    list.add("status: ${it.status.toString().escapeMarkdown()}")
                }
                list.joinToString("\n")
            }
        } else {
            val list = ArrayList<String>()
            lateinit var photoUrl: String
            lateinit var photoName: String
            val friendConfig = FriendConfigService.findByTG(message.chatId)
            if (friendConfig != null) {
                val friend = bot.qq.qqBot.getFriend(friendConfig.qqId)
                    ?: throw BotException("无法找到好友id(${friendConfig.qqId})")
                list.add("绑定好用id: `${friend.id}`")
                list.add("绑定好用名称: `${friend.nameCardOrNick.escapeMarkdown()}`")
                photoName = "friend-avatar-${friend.id}.png"
                photoUrl = friend.avatarUrl
            } else {
                val group = bot.groupConfigService.findByTg(message.chatId)?.qqGroupId?.let { qqBot.getGroup(it) }
                    ?: return "找不到绑定的qq群"
                val config = bot.groupConfigService.configs.firstOrNull { it.telegramGroupId == message.chatId }
                list.add("绑定群id: `${group.id}`")
                list.add("绑定群名称: `${group.name.escapeMarkdown()}`")
                list.add("绑定群群主: `${group.owner.nick.escapeMarkdown()}`\\(`${group.owner.id}`\\)")
                if (config?.status?.isNotEmpty() == true)
                    list.add("状态: ${config.status.toString().escapeMarkdown()}")
                photoName = "group-avatar-${group.id}.png"
                photoUrl = group.avatarUrl
            }

            val photoPath =
                withVT { BotUtil.downloadImg(photoName, photoUrl, overwrite = true) }
            bot.tg.send {
                messagePhoto(message.chatId, photoPath.pathString, list.joinToString("\n").fmt(ParseMode.MARKDOWN_V2))
            }
            null
        }
    }

}