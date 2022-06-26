package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import net.mamoe.mirai.message.data.source

class BindCommand : AbstractTelegramCommand() {

    override val command = "bind"
    override val help: String = "绑定群组或用户名"
    override val onlyAdmin = true
    override val onlySupperAdmin = false
    override val parseMode = ParseMode.MARKDOWN_V2

    private val cacheService = ContextHolder.cacheService

    override fun execute(update: Update, message: Message): String? {
        val client = ContextHolder.telegramBot
        val qqBot = ContextHolder.qqBot
        val param = message.text?.param() ?: "参数错误"
        return if (message.isGroupMessage() || message.isSuperGroupMessage()) {
            if (message.isReply()) {
                val replyMessage = message.replyToMessage!!
                if (param.isNotBlank()) {
                    val user = replyMessage.from!!
                    if (user.username == client.username) {
                        val qqMsg = cacheService.getQQByTg(replyMessage) ?: return "找不到该qq信息"
                        UserConfig.bindName(qq = qqMsg.source.fromId, bindingName = param)
                        "qq`${qqMsg.source.fromId}` 绑定名称为 `${param.format2Markdown()}`"
                    } else {
                        UserConfig.bindName(user.id, null, param, user.username)
                        "`${user.firstName.format2Markdown()}` 绑定名称为 `${param.format2Markdown()}`"
                    }
                } else {
                    "绑定名称不能为空"
                }
            } else {
                if (UserConfig.superAdmins.contains(message.from!!.id)) {
                    try {
                        val qq = param.toLong()
                        qqBot.getGroup(qq)?.let {
                            GroupConfig.add(message.chat.id, qq, message.chat.title!!)
                            "绑定成功\n\n" +
                                    "绑定QQ群id: `${it.id}`\n" +
                                    "绑定QQ群名称: `${it.name.format2Markdown()}`\n" +
                                    "绑定QQ群主: `${it.owner.nick.format2Markdown()}`\\(`${it.owner.id}`\\)\n"
                        } ?: "没有找到qq群`$qq`"
                    } catch (e: NumberFormatException) {
                        "转换qq群组id错误"
                    }
                } else {
                    "绑定群组操作需要超级管理员权限"
                }
            }
        } else if (message.isUserMessage() && UserConfig.superAdmins.contains(message.from!!.id)) {
            val usernameBinds =
                UserConfig.configs.filter { it.bindingName != null }.joinToString("\n") { "`${it.username?.format2Markdown() ?: it.tg}` \\<\\=\\> `${it.bindingName!!.format2Markdown()}`" }
            val groupBindings = GroupConfig.configs.joinToString("\n") { "`${it.tg}` \\<\\=\\> `${it.qq}` \\#${qqBot.getGroup(it.qq)?.name?.format2Markdown() ?: "找不到该QQ群"}" }
            "用户名绑定：\n$usernameBinds\n\nQ群绑定：\n$groupBindings"
        } else null
    }
}