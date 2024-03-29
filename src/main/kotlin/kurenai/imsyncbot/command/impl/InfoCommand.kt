package kurenai.imsyncbot.command.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.dfs
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.SendPhoto
import moe.kurenai.tdlight.util.MarkdownUtil.fm2md
import net.mamoe.mirai.message.data.source
import java.time.Instant
import java.time.ZoneId

class InfoCommand : AbstractTelegramCommand() {

    override val command = "info"
    override val help: String = "查看用户或群信息"
    override val onlyGroupMessage = true
    override val parseMode = ParseMode.MARKDOWN_V2

    override suspend fun execute(update: Update, message: Message): String? {
        val bot = getBotOrThrow()
        val qqBot = bot.qq.qqBot
        return if (message.isReply()) {
            val replyMessage = message.replyToMessage!!
            val user = replyMessage.from!!
            if (user.isBot && user.username == bot.tg.username) {
                val qqMsg = CacheService.getQQByTg(replyMessage)
                if (qqMsg != null) {
                    val userId = qqMsg.source.fromId
                    val qqGroup = bot.groupConfig.tgQQ[message.chat.id]?.let { qqBot.getGroup(it) }
                    if (qqGroup == null) {
                        return "找不到绑定的qq群"
                    } else {
                        val config = bot.userConfig.items.firstOrNull { it.qq == userId }
                        val member = qqGroup[userId]
                        if (member != null) {

                            val list = ArrayList<String>()
                            list.add("id: `${member.id}`")
                            list.add("昵称: `${member.nick.fm2md()}`")
                            if (member.remark.isNotBlank())
                                list.add("备注: `${member.nameCard.fm2md()}`")
                            if (member.nameCard.isNotBlank())
                                list.add("名片: `${member.nameCard.fm2md()}`")
                            if (member.specialTitle.isNotBlank())
                                list.add("头衔: `${member.specialTitle.fm2md()}`")
                            list.add("身份: ${member.permission}")
                            if (config?.bindingName?.isNotBlank() == true)
                                list.add("绑定名称: `${config.bindingName!!.fm2md()}`")
                            if (member.isMuted)
                                list.add("被禁言${member.muteTimeRemaining}s")
                            list.add(
                                "入群时间: `${
                                    Instant.ofEpochSecond(member.joinTimestamp.toLong()).atZone(ZoneId.systemDefault())
                                        .format(dfs).fm2md()
                                }`"
                            )
                            list.add(
                                "最后发言: `${
                                    Instant.ofEpochSecond(member.lastSpeakTimestamp.toLong())
                                        .atZone(ZoneId.systemDefault()).format(dfs).fm2md()
                                }`"
                            )
                            if (config?.status?.isNotEmpty() == true)
                                list.add("状态: ${config.status.toString().fm2md()}")

                            withContext(Dispatchers.IO) {
                                val file = BotUtil.downloadImg("avatar-${member.id}.png", member.avatarUrl).toFile()
                                SendPhoto(message.chatId, InputFile(file)).apply {
                                    caption = list.joinToString("\n")
                                    parseMode = ParseMode.MARKDOWN_V2
                                }.send()
                            }
                            null
                        } else {
                            "找不到qq账户$userId"
                        }
                    }
                } else "找不到该qq信息"
            } else {
                val list = ArrayList<String>()
                list.add("id: `${user.id}`")
                list.add("username: `${user.username?.format2Markdown()}`")
                list.add("firstName: `${user.firstName.format2Markdown()}`")
                list.add("lastName: `${user.lastName?.format2Markdown()}`")
                list.add("isBot: ${user.isBot}")
                bot.userConfig.items.firstOrNull {
                    it.tg == user.id
                }?.let {
                    list.add("status: ${it.status.toString().fm2md()}")
                }
                list.joinToString("\n")
            }
        } else {
            val group = bot.groupConfig.tgQQ[message.chat.id]?.let { qqBot.getGroup(it) }
            if (group == null) {
                return "找不到绑定的qq群"
            } else {
                val list = ArrayList<String>()
                val config = bot.groupConfig.items.firstOrNull { it.tg == message.chat.id }
                list.add("绑定群id: `${group.id}`")
                list.add("绑定群名称: `${group.name.format2Markdown()}`")
                list.add("绑定群群主: `${group.owner.nick.format2Markdown()}`\\(`${group.owner.id}`\\)")
                if (config?.status?.isNotEmpty() == true)
                    list.add("状态: ${config.status.toString().format2Markdown()}")

                val path = BotUtil.downloadImg("group-avatar-${group.id}.png", group.avatarUrl)
                SendPhoto(message.chatId, InputFile(path.toFile())).apply {
                    caption = list.joinToString("\n")
                    parseMode = ParseMode.MARKDOWN_V2
                }.send()
                null
            }
        }
    }

}