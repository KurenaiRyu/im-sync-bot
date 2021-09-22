package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.Command
import kurenai.imsyncbot.repository.BindingGroupRepository
import net.mamoe.mirai.event.events.MessageEvent
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class GroupCommand(
    private val repository: BindingGroupRepository,
) : Command {

    override fun execute(update: Update): Boolean {
        if (!ContextHolder.masterOfTg.contains(update.message.from.id)) {
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().chatId(update.message.chatId.toString()).text("Only for master.")
                    .replyToMessageId(update.message.messageId).build()
            )
            return false
        }

        val rec = doExec(update.message.text)
        if (rec.isNotEmpty()) {
            val client = ContextHolder.telegramBotClient
            client.execute(SendMessage.builder().chatId(update.message.chatId.toString()).text(rec).build())
        }
        return false
    }

    override fun execute(event: MessageEvent): Boolean {
        doExec(event.message.contentToString())
        return false
    }

    override fun match(text: String): Boolean {
        return text.startsWith("/group", true)
    }

    override fun getHelp(): String {
        return "/group <id> 显示qq或者tg群信息(该bot已加入的)"
    }

    private fun doExec(text: String): String {
        val content = text.substring(6).trim()
        return if (content.isEmpty()) {
            "command error.\nexample command: /group 123456"
        } else {
            try {
                val chat = ContextHolder.telegramBotClient.execute(GetChat.builder().chatId(content).build())
                if (chat.isSuperGroupChat || chat.isGroupChat || chat.isChannelChat) {
                    "id: ${chat.id}\ntitle: ${chat.title}\ndescription: ${chat.description}\n"
                } else {
                    "Group not found."
                }
            } catch (e: Exception) {
                val group = ContextHolder.qqBot.getGroup(content.toLong())
                if (group == null) "Group not found."
                else "id: ${group.id}\ntitle: ${group.name}"
            }
        }
    }


}