package kurenai.imsyncbot.command

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.SendMessage
import mu.KotlinLogging
import net.mamoe.mirai.event.events.MessageEvent

object DelegatingCommand {

    private val log = KotlinLogging.logger {}

    private val tgHandlers = ArrayList<AbstractTelegramCommand>()
    private val qqHandlers = ArrayList<AbstractQQCommand>()

    fun execute(update: Update, message: Message) {
        val command = message.entities!!
            .first { it.type == MessageEntityType.BOT_COMMAND }
            .text!!
            .replace("/", "")
            .replace("@${ContextHolder.telegramBot.username}", "")
        if (command == "help") {
            handleHelp(message)
            return
        }
        var reply = true
        var parseMode: String? = null
        var msg: String? = null
        for (handler in tgHandlers) {
            if (handler.command == command) {
                log.debug { "Match ${handler.name}" }
                msg = if (handler.onlyMaster && UserConfig.masterTg != message.from?.id) {
                    "该命令只允许主人执行"
                } else if (handler.onlySupperAdmin && !UserConfig.superAdmins.contains(message.from?.id)) {
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

    suspend fun execute(event: MessageEvent): Int {
        var matched = false
        for (handler in qqHandlers) {
            if (handler.execute(event) == 1) {
                matched = true
                break
            }
        }
        return if (matched) 1 else 0
    }

    fun addTgHandle(handler: AbstractTelegramCommand) {
        tgHandlers.removeIf { it.name == handler.name }
        tgHandlers.add(handler)
    }

    fun addQQHandle(handler: AbstractQQCommand) {
        qqHandlers.add(handler)
    }

    private fun handleHelp(message: Message) {
        val sb = StringBuilder("Command list")
        for (handler in tgHandlers) {
            sb.append("\n----------------\n")
            sb.append("/${handler.command} ${handler.name}\n")
            sb.append(handler.help)
        }
        SendMessage(message.chatId.toString(), sb.toString()).send()
    }


}