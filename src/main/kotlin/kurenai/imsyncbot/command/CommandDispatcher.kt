package kurenai.imsyncbot.command

import dev.zacsweers.metro.Inject
import it.tdlight.jni.TdApi.*
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.exception.CommandException
import kurenai.imsyncbot.service.Permission
import kurenai.imsyncbot.utils.getLogger
import kurenai.imsyncbot.utils.telegram.ParseMode
import kurenai.imsyncbot.utils.telegram.messageId
import kurenai.imsyncbot.utils.telegram.text
import kurenai.imsyncbot.utils.telegram.userSender

@Inject
class CommandDispatcher(
    cmd: Set<AbstractTelegramCommand>
) {

    private val log = getLogger()

    internal val commands: Map<String, AbstractTelegramCommand> =
        cmd.associateBy { handler ->
            handler.command.lowercase().also {
                log.debug("Registry command: $it")
            }
        }

    context(bot: ImSyncBot)
    suspend fun execute(message: Message, commandEntity: TextEntity) {
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
        for ((cmd, handler) in commands) {
            if (cmd.equals(command, ignoreCase = true)) {
                log.info("Match ${handler.name}")
                val permission = bot.userConfigService.getPermission(
                    bot.tg.getUser(
                        message.userSender()?.userId ?: error("非用户无权限调用")
                    )
                )
                val permissionLevel = permission.level
                val isSupperAdmin = permissionLevel <= Permission.SUPPER_ADMIN.level
                val input = content.text.text.substring(commandEntity.length).trim()
                responseMsg = if (handler.onlyMaster && permission != Permission.MASTER) {
                    "该命令只允许主人执行"
                } else if (handler.onlySupperAdmin && !isSupperAdmin) {
                    "该命令只允许超级管理员执行"
                } else if (handler.onlyAdmin && permissionLevel > Permission.ADMIN.level) {
                    "该命令只允许管理员执行"
                } else if (handler.onlyUserMessage && typeConstructor != ChatTypePrivate.CONSTRUCTOR) {
                    "该命令只允许私聊执行"
                } else if (handler.onlyGroupMessage && !(typeConstructor == ChatTypeBasicGroup.CONSTRUCTOR || typeConstructor == ChatTypeSupergroup.CONSTRUCTOR)) {
                    "该命令只允许群组执行"
                } else if (handler.onlyReply && (message.replyTo?.messageId ?: 0) != 0L) {
                    "需要引用一条消息"
                } else {
                    try {
                        handler.execute(message, sender, input)?.also {
                            reply = handler.reply
                            parseMode = handler.parseMode
                        }
                    } catch (e: BotException) {
                        throw e
                    } catch (e: Exception) {
                        throw CommandException("Execute $command command fail", e)
                    }
                }
                break
            }
        }

        responseMsg?.takeIf { it.isNotBlank() }?.let {
            bot.tg.sendMessageText(it, chat.id, parseMode, replayToMessageId = if (reply) message.id else 0)
        }
    }


}