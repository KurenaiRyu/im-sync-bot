package kurenai.imsyncbot.command

import com.google.common.base.CaseFormat
import it.tdlight.jni.TdApi.*
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.qqCommands
import kurenai.imsyncbot.tgCommands
import kurenai.imsyncbot.utils.ParseMode
import kurenai.imsyncbot.utils.TelegramUtil.text
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
                val isSupperAdmin = bot.userConfig.superAdmins.contains(sender.userId)
                val input = content.text.text.substring(commandEntity.length).trim()
                responseMsg = if (isSupperAdmin && input == "ban") {
                    handleBan(bot, message.chatId, cmd)
                    "Banned command: ${cmd.command}"
                } else if (isSupperAdmin && input == "unban") {
                    handleUnban(bot, message.chatId, cmd)
                    "Unbanned command: ${cmd.command}"
                } else if (bot.groupConfig.statusContain(message.chatId, cmd.getCommandBannedStatus())) {
                    log.debug("Command was banned for group[${message.chatId}(${message.chatId})].")
                    return
                } else {
                    if (cmd.onlyMaster && bot.userConfig.masterTg != sender.userId) {
                        "该命令只允许主人执行"
                    } else if (cmd.onlySupperAdmin && !isSupperAdmin) {
                        "该命令只允许超级管理员执行"
                    } else if (cmd.onlyAdmin && !bot.userConfig.admins.contains(sender.userId)) {
                        "该命令只允许管理员执行"
                    } else if (cmd.onlyUserMessage && typeConstructor != ChatTypePrivate.CONSTRUCTOR) {
                        "该命令只允许私聊执行"
                    } else if (cmd.onlyGroupMessage && !(typeConstructor == ChatTypeBasicGroup.CONSTRUCTOR || typeConstructor == ChatTypeSupergroup.CONSTRUCTOR)) {
                        "该命令只允许群组执行"
                    } else if (cmd.onlyReply && message.replyToMessageId != 0L) {
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
                }
                break
            }
        }

        responseMsg?.takeIf { it.isNotBlank() }?.let {
            bot.tg.sendMessageText(it, chat.id, parseMode, replayToMessageId = if (reply) message.id else null)
        }
    }

    private fun handleUnban(bot: ImSyncBot, groupId: Long, handler: AbstractTelegramCommand) {
        bot.groupConfig.removeStatus(groupId, handler.getCommandBannedStatus())
        log.info("Unbanned command: ${handler.command}")
    }

    private fun handleBan(bot: ImSyncBot, groupId: Long, handler: AbstractTelegramCommand) {
        bot.groupConfig.addStatus(groupId, handler.getCommandBannedStatus())
        log.info("Banned command: ${handler.command}")
    }

    private fun AbstractTelegramCommand.getCommandBannedStatus(): String {
        return "${CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, this::class.simpleName!!)}_BANNED"
    }

    private fun AbstractQQCommand.getCommandBannedStatus(): String {
        return "${
            CaseFormat.UPPER_CAMEL.to(
                CaseFormat.UPPER_UNDERSCORE,
                this::class.simpleName!!.replace("QQ", "")
            )
        }_BANNED"
    }

    suspend fun execute(event: MessageEvent): Int {
        val bot = getBotOrThrow()
        var matched = false
        for (handler in qqCommands) {
            if (bot.groupConfig.items.any { it.qq == event.subject.id && it.status.contains(handler.getCommandBannedStatus()) })
                if (handler.execute(event) == 1) {
                    matched = true
                    break
                }
        }
        return if (matched) 1 else 0
    }


}