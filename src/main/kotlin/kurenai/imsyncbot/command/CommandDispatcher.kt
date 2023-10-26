package kurenai.imsyncbot.command

import it.tdlight.jni.TdApi.*
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.qqCommands
import kurenai.imsyncbot.service.Permission
import kurenai.imsyncbot.tgCommands
import kurenai.imsyncbot.utils.ParseMode
import kurenai.imsyncbot.utils.TelegramUtil.replyToMessageId
import kurenai.imsyncbot.utils.TelegramUtil.text
import kurenai.imsyncbot.utils.TelegramUtil.userSender
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.event.events.MessageEvent

object CommandDispatcher {

    private val log = getLogger()

    suspend fun execute(bot: ImSyncBot, message: Message, commandEntity: TextEntity) {
        val content = message.content
        val sender = message.senderId
        if (content !is MessageText || sender !is MessageSenderUser) return

        val commandText = commandEntity.text(content.text.text)
        val index = commandText.indexOf("@")
        val chat = bot.tg.send { GetChat(message.chatId) }

        if (chat.type !is ChatTypePrivate && (index != -1 && commandText.substring(index + 1) != bot.tg.getUsername())) return

        val command = if (index == -1) commandText.substring(1) else commandText.substring(1, index)

        var reply = true
        var parseMode: ParseMode = ParseMode.TEXT

        val typeConstructor = chat.type.constructor

        var responseMsg: String? = null
        for (cmd in tgCommands) {
            if (cmd.command.lowercase() == command.lowercase()) {
                log.info("Match ${cmd.name}")
                val permission = bot.userConfigService.getPermission(
                    bot.tg.getUser(
                        message.userSender()?.userId ?: error("非用户无权限调用")
                    )
                )
                val permissionLevel = permission.level
                val isSupperAdmin = permissionLevel <= Permission.SUPPER_ADMIN.level
                val input = content.text.text.substring(commandEntity.length).trim()
                responseMsg = if (cmd.onlyMaster && permission != Permission.MASTER) {
                    "该命令只允许主人执行"
                } else if (cmd.onlySupperAdmin && !isSupperAdmin) {
                    "该命令只允许超级管理员执行"
                } else if (cmd.onlyAdmin && permissionLevel > Permission.ADMIN.level) {
                    "该命令只允许管理员执行"
                } else if (cmd.onlyUserMessage && typeConstructor != ChatTypePrivate.CONSTRUCTOR) {
                    "该命令只允许私聊执行"
                } else if (cmd.onlyGroupMessage && !(typeConstructor == ChatTypeBasicGroup.CONSTRUCTOR || typeConstructor == ChatTypeSupergroup.CONSTRUCTOR)) {
                    "该命令只允许群组执行"
                } else if (cmd.onlyReply && message.replyToMessageId() != 0L) {
                    "需要引用一条消息"
                } else {
                    try {
                        cmd.execute(bot, message, sender, input)?.also {
                            reply = cmd.reply
                            parseMode = cmd.parseMode
                        }
                    } catch (e: BotException) {
                        e.message
                    }
                }
                break
            }
        }

        responseMsg?.takeIf { it.isNotBlank() }?.let {
            bot.tg.sendMessageText(it, chat.id, parseMode, replayToMessageId = if (reply) message.id else null)
        }
    }

    suspend fun execute(event: MessageEvent): Int {
        val bot = getBotOrThrow()
        var matched = false
        for (handler in qqCommands) {
            if (bot.groupConfigService.configs.any { it.qqGroupId == event.subject.id }
                && handler.execute(event) == 1) {
                matched = true
                break
            }
        }
        return if (matched) 1 else 0
    }


}