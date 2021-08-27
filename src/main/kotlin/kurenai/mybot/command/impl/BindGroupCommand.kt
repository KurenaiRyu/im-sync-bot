package kurenai.mybot.command.impl

import kurenai.mybot.ContextHolder
import kurenai.mybot.command.Command
import kurenai.mybot.domain.BindingGroup
import kurenai.mybot.repository.BindingGroupRepository
import mu.KotlinLogging
import net.mamoe.mirai.event.events.MessageEvent
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class BindGroupCommand(
    private val repository: BindingGroupRepository,
) : Command {

    private val log = KotlinLogging.logger {}

    override fun execute(update: Update): Boolean {
        if (update.message.from.id != ContextHolder.masterOfTg) {
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().chatId(update.message.chatId.toString()).text("Only for master.")
                    .replyToMessageId(update.message.messageId).build()
            )
        }

        val rec = doExec(update.message.text)
        if (rec.isNotEmpty()) {
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().chatId(update.message.chatId.toString()).text(rec).replyToMessageId(update.message.messageId).build()
            )
        }
        return false
    }

    override fun execute(event: MessageEvent): Boolean {
        doExec(event.message.contentToString())
        return false
    }

    override fun match(text: String): Boolean {
        return text.startsWith("/bindGroup", true)
    }

    override fun getHelp(): String {
        return "/bindGroup 显示当前绑定列表\n/bindGroup <qqGroupId>:<tgGroupId(chatId)> 增加一对绑定关系\n/bindGroup rm <qqGroupId(tgGroupId)> 移除该id关联绑定(可以是qq或者tg)"
    }

    private fun doExec(text: String): String {
        val content = text.substring(10).trim()
        if (content.isEmpty()) {
            val sb = StringBuilder("qq-telegram group binding list\n----------------------")
            val qqBot = ContextHolder.qqBot
            ContextHolder.qqTgBinding.forEach {
                sb.append("\n${it.key} <=> ${it.value}")
                qqBot.getGroup(it.key)?.let { group ->
                    sb.append(" #${group.name}")
                }
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
                "Command error.\nexample command: /bindGroup rm 123456"
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
        return "Command error.\nexample command: /bindGroup 123456:654321"
    }


}