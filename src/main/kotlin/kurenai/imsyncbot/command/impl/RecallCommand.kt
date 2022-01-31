package kurenai.imsyncbot.command.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.command.AbstractCommand
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import mu.KotlinLogging
import net.mamoe.mirai.contact.recallMessage
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class RecallCommand(
    val cacheService: CacheService
) : AbstractCommand() {

    private val log = KotlinLogging.logger {}
    override val help: String = "撤回消息"
    override val command: String = "recall"
    override val onlySupperAdmin: Boolean = false
    override val onlyReply: Boolean = true
    override val reply: Boolean = true

    override fun execute(update: Update, message: Message): String? {
        return try {
            CoroutineScope(Dispatchers.Default).launch {
                val qqMsg = cacheService.getQQByTg(message.replyToMessage)
                if (qqMsg == null)
                    SendMessage(message.chatId.toString(), "未能找到对应的qq消息").send()
                else {
                    ContextHolder.qqBot.getGroup(qqMsg.targetId)?.recallMessage(qqMsg)
                    ContextHolder.telegramBotClient.sendAsync(DeleteMessage(message.chatId.toString(), message.replyToMessage.messageId))
                    ContextHolder.telegramBotClient.sendAsync(DeleteMessage(message.chatId.toString(), message.messageId))
                }
            }
            null
        } catch (e: Exception) {
            log.error(e.message, e)
            "error: ${e.message}"
        }
    }
}