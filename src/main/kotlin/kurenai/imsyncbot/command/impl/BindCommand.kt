package kurenai.imsyncbot.command.impl

import it.tdlight.jni.TdApi.*
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.ParseMode
import kurenai.imsyncbot.utils.TelegramUtil.escapeMarkdownChar
import kurenai.imsyncbot.utils.TelegramUtil.textOrCaption
import kurenai.imsyncbot.utils.TelegramUtil.userSender
import kurenai.imsyncbot.utils.TelegramUtil.username
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.message.data.source

private val log = getLogger()

class BindCommand : AbstractTelegramCommand() {

    override val command = "bind"
    override val help: String = "绑定群组或用户名"
    override val onlyAdmin = false
    override val onlySupperAdmin = true
    override val onlyGroupMessage: Boolean = true
    override val parseMode: ParseMode = ParseMode.MARKDOWN_V2

    override suspend fun execute(bot: ImSyncBot, message: Message, sender: MessageSenderUser, input: String): String? {
        val param = message.content.textOrCaption()?.text?.substringAfter(' ') ?: return "参数错误"
        val qqBot = bot.qq.qqBot
        val tg = bot.tg
        val chat = tg.getChat(message.chatId)
        return if (chat.type.constructor == ChatTypeSupergroup.CONSTRUCTOR
            || chat.type.constructor == ChatTypeBasicGroup.CONSTRUCTOR
        ) {
            if (message.replyToMessageId != 0L) {
                tg.getMessage(message.replyInChatId, message.replyToMessageId)?.let { replyMessage ->
                    if (param.isNotBlank()) {
                        val user = tg.getUser(replyMessage) ?: return "找不到用户"
                        if (user == tg.getMe()) {
                            val qqMsg = MessageService.findQQByTg(replyMessage) ?: return "找不到该qq信息"
                            bot.userConfig.bindName(qq = qqMsg.source.fromId, bindingName = param)
                            "qq`${qqMsg.source.fromId}` 绑定名称为 `${param.escapeMarkdownChar()}`"
                        } else {
                            bot.userConfig.bindName(user.id, null, param, user.username())
                            "`${user.firstName.escapeMarkdownChar()}` 绑定名称为 `${param.escapeMarkdownChar()}`"
                        }
                    } else {
                        "绑定名称不能为空"
                    }
                } ?: "找不到引用的消息"
            } else {
                if (bot.userConfig.superAdmins.contains(message.userSender()?.userId)) {
                    try {
                        val qq = param.toLong()
                        qqBot.getGroup(qq)?.let {
                            bot.groupConfigService.bind(chat.id, qq, chat.title)
                            "绑定成功\n\n" +
                                    "绑定QQ群id: `${it.id}`\n" +
                                    "绑定QQ群名称: `${it.name.escapeMarkdownChar()}`\n" +
                                    "绑定QQ群主: `${it.owner.nick.escapeMarkdownChar()}`\\(`${it.owner.id}`\\)\n"
                        } ?: "没有找到qq群`$qq`"
                    } catch (e: NumberFormatException) {
                        "转换qq群组id错误"
                    }
                } else {
                    "绑定群组操作需要超级管理员权限"
                }
            }
        } else if (chat.type.constructor == ChatTypePrivate.CONSTRUCTOR && bot.userConfig.superAdmins.contains(chat.id)) {
            val usernameBinds =
                bot.userConfig.configs.filter { it.bindingName != null }
                    .joinToString("\n") { "`${it.username?.escapeMarkdownChar() ?: it.tg}` \\<\\=\\> `${it.bindingName!!.escapeMarkdownChar()}`" }
            val groupBindings =
                bot.groupConfigService.configs.joinToString("\n") {
                    "`${it.telegramGroupId}` \\<\\=\\> `${it.qqGroupId}` \\#${
                        qqBot.getGroup(
                            it.qqGroupId
                        )?.name?.escapeMarkdownChar() ?: "找不到该QQ群"
                    }"
                }
            "用户名绑定：\n$usernameBinds\n\nQ群绑定：\n$groupBindings"
        } else null
    }
}

class UnbindCommand : AbstractTelegramCommand() {

    override val command = "unbind"
    override val help: String = "解绑群组或用户名"
    override val onlyAdmin = false
    override val onlySupperAdmin = true

    override suspend fun execute(bot: ImSyncBot, message: Message, sender: MessageSenderUser, input: String): String? {
        val param = message.content.textOrCaption()?.text ?: return "参数错误"
        return if (param.isNotBlank()) {
            try {
                bot.groupConfigService.remove(param.toLong())
                "解绑Q群成功"
            } catch (e: Exception) {
                log.error("参数错误", e)
                "参数错误"
            }
        } else {
            if (message.replyToMessageId != 0L) {
                bot.tg.getMessage(message.replyInChatId, message.replyToMessageId)?.let { reply ->
                    val userId = reply.userSender()?.userId
                    if (userId == bot.tg.getMe().id) {
                        val qqMsg = MessageService.findQQByTg(reply)
                        if (qqMsg != null) {
                            bot.userConfig.unbindUsername(qqMsg.source.fromId)
                            "qq[${qqMsg.source.fromId}] 解绑名称成功"
                        } else "找不到该qq信息"
                    } else {
                        if (bot.userConfig.superAdmins.contains(message.userSender()?.userId)) {
                            bot.userConfig.unbindUsername(userId)
                            "$userId 解绑名称成功"
                        } else {
                            "绑定群组操作需要超级管理员权限"
                        }
                    }
                } ?: "找不到引用的消息"
            } else {
                bot.groupConfigService.remove(message.chatId)
                "解绑Q群成功"
            }
        }
    }
}