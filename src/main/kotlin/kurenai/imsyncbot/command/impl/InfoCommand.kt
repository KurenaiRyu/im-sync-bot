package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import java.time.Instant
import java.time.ZoneId

@Component
class InfoCommand(
    val cacheService: CacheService
) : AbstractCommand() {

    override val command = "info"
    override val help: String = "查看用户或群信息"
    override val onlyGroupMessage = true
    override val parseMode = ParseMode.MARKDOWNV2

    override fun execute(update: Update, message: Message): String? {
        return if (message.isReply) {
            val user = message.replyToMessage.from
            if (user.isBot && user.userName == ContextHolder.telegramBotClient.botUsername) {
                val qqMsg = cacheService.getQQByTg(message.replyToMessage)
                if (qqMsg != null) {
                    val userId = qqMsg.fromId
                    val qqGroup = GroupConfig.tgQQ[message.chatId]?.let { ContextHolder.qqBot.getGroup(it) }
                    if (qqGroup == null) {
                        return "找不到绑定的qq群"
                    } else {
                        val config = UserConfig.configs.firstOrNull { it.qq == userId }
                        val member = qqGroup[userId]
                        if (member != null) {

                            val list = ArrayList<String>()
                            list.add("id: `${member.id}`")
                            list.add("昵称: `${member.nick.format2Markdown()}`")
                            if (member.remark.isNotBlank())
                                list.add("备注: `${member.nameCard.format2Markdown()}`")
                            if (member.nameCard.isNotBlank())
                                list.add("名片: `${member.nameCard.format2Markdown()}`")
                            if (member.specialTitle.isNotBlank())
                                list.add("头衔: `${member.specialTitle.format2Markdown()}`")
                            list.add("身份: ${member.permission}")
                            if (config?.bindingName?.isNotBlank() == true)
                                list.add("绑定名称: `${config.bindingName!!.format2Markdown()}`")
                            if (member.isMuted)
                                list.add("被禁言${member.muteTimeRemaining}s")
                            list.add("入群时间: `${Instant.ofEpochSecond(member.joinTimestamp.toLong()).atZone(ZoneId.systemDefault()).format(ContextHolder.dfs).format2Markdown()}`")
                            list.add("最后发言: `${Instant.ofEpochSecond(member.lastSpeakTimestamp.toLong()).atZone(ZoneId.systemDefault()).format(ContextHolder.dfs).format2Markdown()}`")
                            if (config?.status?.isNotEmpty() == true)
                                list.add("状态: ${config.status.toString().format2Markdown()}")

                            val file = BotUtil.downloadImg("avatar-${member.id}.png", member.avatarUrl)
                            ContextHolder.telegramBotClient.send(SendPhoto(message.chatId.toString(), InputFile(file)).apply {
                                caption = list.joinToString("\n")
                                parseMode = ParseMode.MARKDOWNV2
                            })
                            null
                        } else {
                            "找不到qq账户$userId"
                        }
                    }
                } else "找不到该qq信息"
            } else {
                val list = ArrayList<String>()
                list.add("id: `${user.id}`")
                list.add("username: `${user.userName?.format2Markdown()}`")
                list.add("firstName: `${user.firstName.format2Markdown()}`")
                list.add("lastName: `${user.lastName?.format2Markdown()}`")
                list.add("isBot: ${user.isBot}")
                list.joinToString("\n")
            }
        } else {
            val group = GroupConfig.tgQQ[message.chatId]?.let { ContextHolder.qqBot.getGroup(it) }
            if (group == null) {
                return "找不到绑定的qq群"
            } else {
                val list = ArrayList<String>()
                val config = GroupConfig.configs.firstOrNull { it.tg == message.chatId }
                list.add("绑定群id: `${group.id}`")
                list.add("绑定群名称: `${group.name.format2Markdown()}`")
                list.add("绑定群群主: `${group.owner.nick.format2Markdown()}`\\(`${group.id}`\\)")
                if (config?.status?.isNotEmpty() == true)
                    list.add("状态: ${config.status.toString().format2Markdown()}")

                val file = BotUtil.downloadImg("group-avatar-${group.id}.png", group.avatarUrl)
                ContextHolder.telegramBotClient.send(SendPhoto(message.chatId.toString(), InputFile(file)).apply {
                    caption = list.joinToString("\n")
                    parseMode = ParseMode.MARKDOWNV2
                })
                null
            }
        }
    }

}