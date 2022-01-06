package kurenai.imsyncbot.command

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.exception.BotException
import mu.KotlinLogging
import net.mamoe.mirai.event.events.MessageEvent
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.EntityType
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

object DelegatingCommand {

    private val log = KotlinLogging.logger {}

    private val handlers = ArrayList<AbstractCommand>()

    fun execute(update: Update, message: Message) {
        val command = message.entities
            .first { it.type == EntityType.BOTCOMMAND }
            .text
            .replace("/", "")
            .replace("@${ContextHolder.telegramBotClient.botUsername}", "")
        if (command == "help") {
            handleHelp(message)
            return
        }
        var reply = true
        var parseMode: String? = null
        var msg: String? = null
        for (handler in handlers) {
            if (handler.command == command) {
                log.debug { "Match ${handler.name}" }
                msg = if (handler.onlyMaster && UserConfig.masterTg != message.from.id) {
                    "该命令只允许主人执行"
                } else if (handler.onlySupperAdmin && !UserConfig.superAdmins.contains(message.from.id)) {
                    "该命令只允许超级管理员执行"
                } else if (handler.onlyAdmin && !UserConfig.admins.contains(message.from.id)) {
                    "该命令只允许管理员执行"
                } else if (handler.onlyUserMessage && !message.isUserMessage) {
                    "该命令只允许私聊执行"
                } else if (handler.onlyGroupMessage && !(message.isSuperGroupMessage || message.isGroupMessage)) {
                    "该命令只允许群组执行"
                } else if (handler.onlyReply && !(message.isReply)) {
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
            ContextHolder.telegramBotClient.send(SendMessage(message.chatId.toString(), msg).apply {
                parseMode?.let { this.parseMode = it }
                if (reply) this.replyToMessageId = message.messageId
            })
        }
    }

    fun execute(event: MessageEvent) {

    }

    fun add(handler: AbstractCommand) {
        handlers.removeIf { it.name == handler.name }
        handlers.add(handler)
    }

    private fun handleHelp(message: Message) {
        val sb = StringBuilder("Command list")
        for (handler in handlers) {
            sb.append("\n----------------\n")
            sb.append("/${handler.command} ${handler.name}\n")
            sb.append(handler.help)
        }
        ContextHolder.telegramBotClient.send(
            SendMessage(message.chatId.toString(), sb.toString())
        )
    }


}