package kurenai.imsyncbot.command

import com.google.common.base.CaseFormat
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.inlineCommands
import kurenai.imsyncbot.qqCommands
import kurenai.imsyncbot.telegram.TelegramBot
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.tgCommands
import kurenai.imsyncbot.utils.reflections
import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.inline.InlineQuery
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.SendMessage
import mu.KotlinLogging
import net.mamoe.mirai.event.events.MessageEvent

object DelegatingCommand {

    private val log = KotlinLogging.logger {}

    init {
        reflections.getSubTypesOf(AbstractQQCommand::class.java)
    }

    suspend fun execute(update: Update, message: Message) {
        val command = message.entities!!
            .first { it.type == MessageEntityType.BOT_COMMAND }
            .text!!
            .replace("/", "")
            .replace("@${TelegramBot.username}", "")
        if (command == "help") {
            handleHelp(message)
            return
        }
        var reply = true
        var parseMode: String? = null
        var msg: String? = null
        for (handler in tgCommands) {
            if (handler.command == command) {
                log.debug { "Match ${handler.name}" }
                val isSupperAdmin = UserConfig.superAdmins.contains(message.from?.id)
                val param = message.text?.replace("/${handler.command}", "")?.trim()
                msg = if (isSupperAdmin && param == "ban") {
                    handleBan(message.chat.id, handler)
                    "Banned command: ${handler.command}"
                } else if (isSupperAdmin && param == "unban") {
                    handleUnban(message.chat.id, handler)
                    "Unbanned command: ${handler.command}"
                } else if (GroupConfig.statusContain(message.chat.id, handler.getCommandBannedStatus())) {
                    log.debug("Command was banned for group[${message.chat.title}(${message.chat.id})].")
                    return
                } else {
                    if (handler.onlyMaster && UserConfig.masterTg != message.from?.id) {
                        "该命令只允许主人执行"
                    } else if (handler.onlySupperAdmin && !isSupperAdmin) {
                        "该命令只允许超级管理员执行"
                    } else if (handler.onlyAdmin && !UserConfig.admins.contains(message.from?.id)) {
                        "该命令只允许管理员执行"
                    } else if (handler.onlyUserMessage && !message.isUserMessage()) {
                        "该命令只允许私聊执行"
                    } else if (handler.onlyGroupMessage && !(message.isSuperGroupMessage() || message.isGroupMessage())) {
                        "该命令只允许群组执行"
                    } else if (handler.onlyReply && !(message.isReply())) {
                        "需要引用一条消息"
                    } else {
                        try {
                            handler.execute(update, message)?.also {
                                reply = handler.reply
                                parseMode = handler.parseMode
                            }
                        } catch (e: BotException) {
                            e.message
                        }
                    }
                }
                break
            }
        }
        msg?.let {
            SendMessage(message.chatId, msg).apply {
                parseMode?.let { this.parseMode = it }
                if (reply) this.replyToMessageId = message.messageId
            }.send()
        }
    }

    private fun handleUnban(groupId: Long, handler: AbstractTelegramCommand) {
        GroupConfig.removeStatus(groupId, handler.getCommandBannedStatus())
        log.info("Unbanned command: ${handler.command}")
    }

    private fun handleBan(groupId: Long, handler: AbstractTelegramCommand) {
        GroupConfig.addStatus(groupId, handler.getCommandBannedStatus())
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

    fun handleInlineQuery(update: Update, inlineQuery: InlineQuery) {
        if (inlineCommands.isEmpty()) return

        if (inlineQuery.query.isBlank()) return
        val query = inlineQuery.query.trim()
        val offset = inlineQuery.offset.takeIf { it.isNotBlank() }?.toInt() ?: 0
        if (offset < 0) return
        try {
            val args = query.split(" ", limit = 2)
            when (args.size) {
                0 -> return
                1 -> {
                    inlineCommands[query]?.run {
                        log.info("Match command ${javaClass.name}")
                        execute(update, inlineQuery, "")
                    }
                }
                2 -> {
                    inlineCommands[args[0]]?.run {
                        log.info("Match command ${javaClass.name}")
                        execute(update, inlineQuery, args[1])
                    }
                }
            }
        } catch (e: Exception) {
            log.error(e.message, e)
//            BangumiBot.tdClient.sendSync(emptyAnswer(inlineQuery.id))
        }
    }

    suspend fun execute(event: MessageEvent): Int {
        var matched = false
        for (handler in qqCommands) {
            if (GroupConfig.items.any { it.qq == event.subject.id && it.status.contains(handler.getCommandBannedStatus()) })
                if (handler.execute(event) == 1) {
                    matched = true
                    break
                }
        }
        return if (matched) 1 else 0
    }

    private suspend fun handleHelp(message: Message) {
        val sb = StringBuilder("Command list")
        for (handler in tgCommands) {
            sb.append("\n----------------\n")
            sb.append("/${handler.command} ${handler.name}\n")
            sb.append(handler.help)
        }
        SendMessage(message.chatId, sb.toString()).send()
    }


}