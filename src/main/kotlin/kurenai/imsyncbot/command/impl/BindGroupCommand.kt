package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.Command
import kurenai.imsyncbot.domain.BindingGroup
import kurenai.imsyncbot.repository.BindingGroupRepository
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import mu.KotlinLogging
import net.mamoe.mirai.event.events.MessageEvent
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class BindGroupCommand(
    private val repository: BindingGroupRepository,
) : Command {

    private val log = KotlinLogging.logger {}
    private val errorMsg = "Command error.\nexample command: /bindGroup rm 123456"

    override fun execute(update: Update): Boolean {
        if (!ContextHolder.masterOfTg.contains(update.message.from.id)) {
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().chatId(update.message.chatId.toString()).text("Only for master.")
                    .replyToMessageId(update.message.messageId).build()
            )
            return false
        }

        doExec(update)
        return false
    }

    override fun execute(event: MessageEvent): Boolean {
        return false
    }

    override fun match(text: String): Boolean {
        return text.startsWith("/bindGroup", true)
    }

    override fun getHelp(): String {
        return "/bindGroup 显示当前绑定列表\n/bindGroup <qqGroupId>:<tgGroupId(chatId)> 增加一对绑定关系\n/bindGroup rm <qqGroupId(tgGroupId)> 移除该id关联绑定(可以是qq或者tg)"
    }

    private fun doExec(update: Update) {
        val text = update.message.text
        val content = text.substring(10).trim()
        val rec = if (content.isEmpty()) {
            val sb = StringBuilder("qq-telegram group binding list\n----------------------".format2Markdown())
            val qqBot = ContextHolder.qqBot
            ContextHolder.qqTgBinding.forEach {
                sb.append("\n`${it.key}` \\<\\=\\> `${it.value.toString().format2Markdown()}`")
                qqBot.getGroup(it.key)?.let { group ->
                    sb.append(" ${group.name.format2Markdown()}")
                }
            }
            val msg = SendMessage(update.message.chatId.toString(), sb.toString())
                .apply {
                    this.parseMode = ParseMode.MARKDOWNV2
                    this.replyToMessageId = update.message.messageId
                }
            try {
                ContextHolder.telegramBotClient.execute(msg)
            } catch (e: Exception) {
                log.debug { "列表发送失败: ${e.message}" }
                ContextHolder.telegramBotClient.execute(msg.apply { this.parseMode = null })
            }
            return
        } else if (content.startsWith("rm", true)) {
            try {
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
                errorMsg
            }
        } else {
            val split = content.split(":")
            if (split.size > 1) {
                try {
                    repository.save(BindingGroup(split[0].toLong(), split[1].toLong()))
                    ContextHolder.qqTgBinding[split[0].toLong()] = split[1].toLong()
                    ContextHolder.tgQQBinding[split[1].toLong()] = split[0].toLong()
                    "Binding success."
                } catch (e: Exception) {
                    log.error(e.message, e)
                    errorMsg
                }
            } else {
                errorMsg
            }
        }
        if (rec.isNotEmpty()) {
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder().chatId(update.message.chatId.toString()).text(rec).replyToMessageId(update.message.messageId).build()
            )
        }
    }


}