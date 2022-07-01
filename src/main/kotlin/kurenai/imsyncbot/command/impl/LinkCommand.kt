package kurenai.imsyncbot.command.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.config.UserStatus
import kurenai.imsyncbot.qq.QQBotClient.bot
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.TelegramBot
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.telegram.sendSync
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.SendMessage
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.source
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

class LinkCommand : AbstractTelegramCommand() {

    companion object {
        val holdLinks = HashMap<Int, Pair<Long, List<Message>>>()
    }

    override val command = "link"
    override val help: String = "链接qq与tg关系，能够在AT时被提醒"

    override val onlySupperAdmin = false
    override val onlyGroupMessage = true
    override val onlyReply = true

    override val reply = true

    private val timer = Timer("clearLink", false)

    override fun execute(update: Update, message: Message): String? {
        val replyMsg = message.replyToMessage!!
        if (replyMsg.from?.username != TelegramBot.username) return "请引用转发的qq消息"
        val qqMsg = CacheService.getQQByTg(replyMsg) ?: return "找不到该qq信息"
        val user = message.from!!
        return if (UserConfig.superAdmins.contains(user.id)) {
            val u = UserConfig.links.firstOrNull { it.tg == user.id && it.qq != null }
            if (u != null) {
                return if (u.status.contains(UserStatus.MASTER)) "master账号无法改变绑定qq"
                else "qq[${qqMsg.source.fromId}]已绑定@${user.username}"
            }

            UserConfig.link(user.id, qqMsg.source.fromId, user.username!!)
            "绑定qq[${qqMsg.source.fromId}]成功"
        } else {
            val qqGroup = bot.getGroup(qqMsg.source.targetId) ?: return "找不到QQ群信息"
            CoroutineScope(Dispatchers.Default).launch {
                qqGroup.sendMessage(At(qqMsg.source.fromId).plus("【${message.from?.firstName ?: "First name not found"}】准备绑定，回复此条消息 accept 完成绑定。不是请无视该信息。"))
                    .also { receipt ->
                        val tips = SendMessage(message.chatId, "请到qq群回复提示消息`accept`进行确认").apply {
                            replyToMessageId = message.messageId
                            parseMode = ParseMode.MARKDOWN_V2
                        }.sendSync()
                        val msgId = receipt.source.ids[0]
                        holdLinks[msgId] = qqMsg.source.fromId to listOf(message, tips)

                        timer.schedule(timerTask {
                            holdLinks.remove(msgId)?.let {
                                SendMessage(message.chatId, "已超时").apply {
                                replyToMessageId = message.messageId
                            }.send()
                            CoroutineScope(Dispatchers.Default).launch {
                                qqGroup.sendMessage(receipt.quote().plus("已超时"))
                            }
                        }
                    }, TimeUnit.MINUTES.toMillis(3))
                }
            }
            null
        }
    }
}