package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.util.getLogger
import net.mamoe.mirai.message.data.source

private val log = getLogger()

class BindCommand : AbstractTelegramCommand() {

    override val command = "bind"
    override val help: String = "绑定群组或用户名"
    override val onlyAdmin = false
    override val onlySupperAdmin = true
    override val onlyGroupMessage: Boolean = true
    override val parseMode = ParseMode.MARKDOWN_V2

    override suspend fun execute(update: Update, message: Message): String? {
        val param = message.text?.param() ?: return "参数错误"
        val bot = getBotOrThrow()
        val qqBot = bot.qq.qqBot
        return if (message.isGroupMessage() || message.isSuperGroupMessage()) {
            if (message.isReply()) {
                val replyMessage = message.replyToMessage!!
                if (param.isNotBlank()) {
                    val user = replyMessage.from!!
                    if (user.username == bot.tg.username) {
                        val qqMsg = CacheService.getQQByTg(replyMessage) ?: return "找不到该qq信息"
                        bot.userConfig.bindName(qq = qqMsg.source.fromId, bindingName = param)
                        "qq`${qqMsg.source.fromId}` 绑定名称为 `${param.format2Markdown()}`"
                    } else {
                        bot.userConfig.bindName(user.id, null, param, user.username)
                        "`${user.firstName.format2Markdown()}` 绑定名称为 `${param.format2Markdown()}`"
                    }
                } else {
                    "绑定名称不能为空"
                }
            } else {
                if (bot.userConfig.superAdmins.contains(message.from!!.id)) {
                    try {
                        val qq = param.toLong()
                        qqBot.getGroup(qq)?.let {
                            bot.groupConfig.add(message.chat.id, qq, message.chat.title!!)
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
        } else if (message.isUserMessage() && bot.userConfig.superAdmins.contains(message.from!!.id)) {
            val usernameBinds =
                bot.userConfig.items.filter { it.bindingName != null }
                    .joinToString("\n") { "`${it.username?.format2Markdown() ?: it.tg}` \\<\\=\\> `${it.bindingName!!.format2Markdown()}`" }
            val groupBindings =
                bot.groupConfig.items.joinToString("\n") { "`${it.tg}` \\<\\=\\> `${it.qq}` \\#${qqBot.getGroup(it.qq)?.name?.format2Markdown() ?: "找不到该QQ群"}" }
            "用户名绑定：\n$usernameBinds\n\nQ群绑定：\n$groupBindings"
        } else null
    }
}

class UnbindCommand : AbstractTelegramCommand() {

    override val command = "unbind"
    override val help: String = "解绑群组或用户名"
    override val onlyAdmin = false
    override val onlySupperAdmin = true

    override suspend fun execute(update: Update, message: Message): String {
        val bot = getBotOrThrow()
        val param = message.text!!.param()
        return if (param.isNotBlank()) {
            try {
                bot.groupConfig.remove(param.toLong())
                "解绑Q群成功"
            } catch (e: Exception) {
                log.error("参数错误", e)
                "参数错误"
            }
        } else {
            if (message.isReply()) {
                val reply = message.replyToMessage!!
                val user = reply.from!!
                if (user.username == bot.tg.username) {
                    val qqMsg = CacheService.getQQByTg(reply)
                    if (qqMsg != null) {
                        bot.userConfig.unbindUsername(qqMsg.source.fromId)
                        "qq[${qqMsg.source.fromId}] 解绑名称成功"
                    } else "找不到该qq信息"
                } else {
                    if (bot.userConfig.superAdmins.contains(message.from!!.id)) {
                        bot.userConfig.unbindUsername(user.id, user.username)
                        "${user.firstName} 解绑名称成功"
                    } else {
                        "绑定群组操作需要超级管理员权限"
                    }
                }
            } else {
                bot.groupConfig.remove(message.chat.id)
                "解绑Q群成功"
            }
        }
    }
}