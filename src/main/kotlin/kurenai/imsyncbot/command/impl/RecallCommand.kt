package kurenai.imsyncbot.command.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.qq.QQBotClient.bot
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.DeleteMessage
import moe.kurenai.tdlight.request.message.SendMessage
import mu.KotlinLogging
import net.mamoe.mirai.contact.recallMessage
import net.mamoe.mirai.message.data.source

class RecallCommand : AbstractTelegramCommand() {

    private val log = KotlinLogging.logger {}
    override val help: String = "撤回消息"
    override val command: String = "recall"
    override val onlySupperAdmin: Boolean = false
    override val onlyReply: Boolean = true
    override val reply: Boolean = true

    override fun execute(update: Update, message: Message): String? {
        val replyMsg = message.replyToMessage!!
        return try {
            CoroutineScope(Dispatchers.Default).launch {
                val qqMsg = CacheService.getQQByTg(replyMsg)
                if (qqMsg == null)
                    SendMessage(message.chatId, "未能找到对应的qq消息").send()
                else {
                    bot.getGroup(qqMsg.source.targetId)?.recallMessage(qqMsg)
                    DeleteMessage(message.chatId, replyMsg.messageId!!).send()
                    DeleteMessage(message.chatId, message.messageId!!).send()
                }
            }
            null
        } catch (e: Exception) {
            log.error(e.message, e)
            "error: ${e.message}"
        }
    }
}