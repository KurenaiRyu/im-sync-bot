package kurenai.imsyncbot.command.impl

import it.tdlight.jni.TdApi.*
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.*
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
            if (message.replyToMessageId() != 0L) {
                tg.getMessage(message.replyInChatId()!!, message.replyToMessageId()!!)?.let { replyMessage ->
                    if (param.isNotBlank()) {
                        val user = tg.getUser(replyMessage) ?: return "找不到用户"
                        if (user.id == tg.getMe().id) {
                            val qqMsg = MessageService.findQQByTg(replyMessage) ?: return "找不到该qq信息"
                            bot.userConfigService.bindName(qq = qqMsg.source.fromId, bindingName = param)
                            "qq`${qqMsg.source.fromId}` 绑定名称为 `${param.escapeMarkdown()}`"
                        } else {
                            bot.userConfigService.bindName(user.id, null, param)
                            "`${user.firstName.escapeMarkdown()}` 绑定名称为 `${param.escapeMarkdown()}`"
                        }
                    } else {
                        "绑定名称不能为空"
                    }
                } ?: "找不到引用的消息"
            } else {
                if (bot.userConfigService.superAdmins.contains(message.userSender()?.userId)) {
                    try {
                        val qq = param.toLong()
                        qqBot.getGroup(qq)?.let {
                            bot.groupConfigService.bind(chat.id, qq, it.name)
                            "绑定成功\n\n" +
                                    "绑定QQ群id: `${it.id}`\n" +
                                    "绑定QQ群名称: `${it.name.escapeMarkdown()}`\n" +
                                    "绑定QQ群主: `${it.owner.nick.escapeMarkdown()}`\\(`${it.owner.id}`\\)\n"
                        } ?: "没有找到qq群`$qq`"
                    } catch (e: NumberFormatException) {
                        "转换qq群组id错误"
                    }
                } else {
                    "绑定群组操作需要超级管理员权限"
                }
            }
        } else if (chat.type.constructor == ChatTypePrivate.CONSTRUCTOR && bot.userConfigService.superAdmins.contains(
                chat.id
            )
        ) {
            val usernameBinds =
                bot.userConfigService.configs.filter { it.bindingName != null }
                    .joinToString("\n") { "`${it.tg}` \\<\\=\\> `${it.bindingName!!.escapeMarkdown()}`" }
            val groupBindings =
                bot.groupConfigService.configs.joinToString("\n") {
                    "`${it.telegramGroupId}` \\<\\=\\> `${it.qqGroupId}` \\#${
                        qqBot.getGroup(
                            it.qqGroupId
                        )?.name?.escapeMarkdown() ?: "找不到该QQ群"
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
            if (message.replyToMessageId() != 0L) {
                bot.tg.getMessage(message.replyInChatId()!!, message.replyToMessageId()!!)?.let { reply ->
                    val userId = reply.userSender()?.userId!!
                    if (userId == bot.tg.getMe().id) {
                        val qqMsg = MessageService.findQQByTg(reply)
                        if (qqMsg != null) {
                            bot.userConfigService.unbindNameByQQ(qqMsg.source.fromId)
                            "qq[${qqMsg.source.fromId}] 解绑名称成功"
                        } else "找不到该qq信息"
                    } else {
                        if (bot.userConfigService.superAdmins.contains(message.userSender()?.userId)) {
                            bot.userConfigService.unbindNameByTG(userId)
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