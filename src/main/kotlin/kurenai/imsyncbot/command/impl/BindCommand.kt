package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class BindCommand(
    val cacheService: CacheService,
) : AbstractCommand() {

    override val command = "bind"
    override val help: String = "绑定群组或用户名"
    override val parseMode = ParseMode.MARKDOWNV2

    override fun execute(update: Update, message: Message): String? {
        val client = ContextHolder.telegramBotClient
        val qqBot = ContextHolder.qqBot
        val param = message.text.body().trim()
        return if (message.isGroupMessage || message.isSuperGroupMessage) {
            if (message.isReply) {
                val user = message.replyToMessage.from
                if (user.userName == client.botUsername) {
                    val qqMsg = cacheService.getQQByTg(message.replyToMessage)
                    if (qqMsg != null) {
                        UserConfig.bindName(qqMsg.fromId, qq = null, param)
                        "qq[`${qqMsg.fromId}`] 绑定名称为 `$param`"
                    } else "找不到该qq信息"
                } else {
                    UserConfig.bindName(user.id, null, param, user.userName)
                    "`${user.firstName}` 绑定名称为 `$param`"
                }
            } else {
                try {
                    val qq = param.toLong()
                    qqBot.getGroup(qq)?.let {
                        GroupConfig.add(qq, message.chatId, message.chat.title)
                        "绑定成功\n\n" +
                                "绑定QQ群id: `${it.id}`\n" +
                                "绑定QQ群名称: `${it.name.format2Markdown()}`\n" +
                                "绑定QQ群主: `${it.owner.nick.format2Markdown()}`\\(`${it.owner.id}`\\)\n"
                    } ?: "没有找到qq群$qq"
                } catch (e: NumberFormatException) {
                    "转换qq群组id错误"
                }
            }
        } else if (message.isUserMessage && UserConfig.superAdmins.contains(message.from.id)) {
            val usernameBinds =
                UserConfig.configs.filter { it.bindingName != null }.joinToString("\n") { "`${it.username?.format2Markdown() ?: it.tg}` \\<\\=\\> `${it.bindingName!!.format2Markdown()}`" }
            val groupBindings = GroupConfig.configs.joinToString("\n") { "`${it.tg}` \\<\\=\\> `${it.qq}` \\#${qqBot.getGroup(it.qq)?.name?.format2Markdown() ?: "找不到该QQ群"}" }
            "用户名绑定：\n$usernameBinds\n\nQ群绑定：\n$groupBindings"
        } else null
    }
}