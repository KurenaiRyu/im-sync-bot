package kurenai.mybot.command.impl

import kurenai.mybot.ContextHolder
import kurenai.mybot.command.Command
import kurenai.mybot.domain.BindingGroup
import kurenai.mybot.repository.BindingGroupRepository
import kurenai.mybot.utils.TelegramUtil
import mu.KotlinLogging
import net.mamoe.mirai.event.events.MessageEvent
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class BindingGroupCommand(
    private val repository: BindingGroupRepository,
) : Command {

    private val log = KotlinLogging.logger {}

    override fun execute(update: Update): Boolean {
        val rec = doExec(update.message.text)
        if (rec.isNotEmpty()) {
            val msg = ContextHolder.telegramBotClient.execute(
                SendMessage.builder().chatId(update.message.chatId.toString()).text(rec).replyToMessageId(update.message.messageId).build()
            )
            TelegramUtil.deleteMsg(msg.chatId, msg.messageId, 5000L)
        }
        return false
    }

    override fun execute(event: MessageEvent): Boolean {
        doExec(event.message.contentToString())
        return false
    }

    override fun match(text: String): Boolean {
        return text.startsWith("/bindingGroup", true)
    }

    override fun getHelp(): String {
        TODO("Not yet implemented")
    }

    private fun doExec(text: String): String {
        val content = text.substring(13).trim()
        if (content.isEmpty()) {
            val sb = StringBuilder("qq-telegram group binding list\n----------------------")
            ContextHolder.qqTgBinding.forEach {
                sb.append("\n[${it.key}]\t-\t[${it.value}]")
            }
            return sb.toString()
        } else if (content.startsWith("rm", true)) {
            return try {
                val group = content.substring(2).trim().toLong()
                var removed = ContextHolder.qqTgBinding.remove(group)
                if (removed != null) {
                    repository.deleteByQq(group)
                    ContextHolder.tgQQBinding.remove(removed)
                } else {
                    removed = ContextHolder.tgQQBinding.remove(group)
                    if (removed != null) {
                        repository.deleteByTg(group)
                        ContextHolder.qqTgBinding.remove(removed)
                    }
                }
                if (removed != null) "Remove success."
                else "Not found group."
            } catch (e: Exception) {
                "Command error.\nexample command: /bindingGroup rm 123456"
            }
        } else {
            val split = content.split(":")
            if (split.size > 1) {
                try {
                    repository.save(BindingGroup(split[0].toLong(), split[1].toLong()))
                    ContextHolder.qqTgBinding[split[0].toLong()] = split[1].toLong()
                    ContextHolder.tgQQBinding[split[1].toLong()] = split[0].toLong()
                    return "Binding success."
                } catch (e: Exception) {
                    log.error(e.message, e)
                }
            }
        }
        return "Command error.\nexample command: /bindingGroup 123456:654321"
    }


}