package kurenai.imsyncbot.command.impl

import com.sksamuel.aedile.core.caffeineBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.imsyncbot.command.AbstractQQCommand
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.config.UserStatus
import kurenai.imsyncbot.getBotOrThrow
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.EditMessageText
import moe.kurenai.tdlight.request.message.SendMessage
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.source
import kotlin.time.Duration.Companion.minutes

class LinkCommand : AbstractTelegramCommand() {

    companion object {
        // <msgId, <qq, [tgMsg, tgTipsMsg]>>
        val linkCache = caffeineBuilder<Int, Pair<Long, List<Message>>> {
            expireAfterWrite = 10.minutes
        }.build()
    }

    override val command = "link"
    override val help: String = "链接qq与tg关系，能够在AT时被提醒"

    override val onlySupperAdmin = false
    override val onlyGroupMessage = true
    override val onlyReply = true

    override val reply = true

    override suspend fun execute(update: Update, message: Message): String? {
        val bot = getBotOrThrow()
        val qqBot = bot.qq.qqBot
        val replyMsg = message.replyToMessage!!
        val user = message.from!!
        val isAdmin = bot.userConfig.superAdmins.contains(user.id)
        val param = update.message?.text?.param() ?: ""
        val anotherId = param.toLongOrNull()
        if (isAdmin && anotherId != null) {
            val name: String
            val qq: Long
            val tg: Long
            if (replyMsg.from?.id == bot.tg.tgBot.me.id) {
                val qqMsg = CacheService.getQQByTg(replyMsg) ?: return "找不到该qq信息"
                qq = qqMsg.source.fromId
                tg = anotherId
                name = user.username?.takeIf { it.isNotBlank() } ?: bot.userConfig.qqUsernames[qq] ?: qq.toString()
                bot.userConfig.link(anotherId, qq, name)
            } else {
                qq = anotherId
                tg = user.id
                name = user.username?.takeIf { it.isNotBlank() } ?: bot.userConfig.qqUsernames[anotherId] ?: anotherId.toString()
                bot.userConfig.link(user.id, anotherId, name)
            }
            return "绑定qq[$qq]-tg[$tg]成功"
        }

        if (replyMsg.from?.id != bot.tg.tgBot.me.id) return "请引用转发的qq消息"
        val qqMsg = CacheService.getQQByTg(replyMsg) ?: return "找不到该qq信息"
        return if (isAdmin) {
            val name = user.username ?: qqMsg.source.fromId.toString()
            val u = bot.userConfig.links.firstOrNull { it.tg == user.id && it.qq != null }
            if (u != null) {
                return if (u.status.contains(UserStatus.MASTER)) "master账号无法改变绑定qq"
                else "qq[${qqMsg.source.fromId}]已绑定@${name}"
            }

            bot.userConfig.link(user.id, qqMsg.source.fromId, name)
            "绑定qq[${qqMsg.source.fromId}]成功"
        } else {
            val qqGroup = qqBot.getGroup(qqMsg.source.targetId) ?: return "找不到QQ群信息"
            CoroutineScope(Dispatchers.Default).launch {
                qqGroup.sendMessage(At(qqMsg.source.fromId).plus("【${message.from?.firstName ?: "First name not found"}】准备绑定，回复此条消息 accept 完成绑定。不是请无视该信息。"))
                    .also { receipt ->
                        val tips = SendMessage(message.chatId, "请到qq群回复提示消息`accept`进行确认").apply {
                            replyToMessageId = message.messageId
                            parseMode = ParseMode.MARKDOWN_V2
                        }.send(bot.tg)
                        val msgId = receipt.source.ids[0]
                        linkCache[msgId] = qqMsg.source.fromId to listOf(message, tips)
                    }
            }
            null
        }
    }
}

class UnlinkCommand : AbstractTelegramCommand() {

    override val command = "unlink"
    override val help: String = "解除qq和tg的链接关系"
    override val onlyGroupMessage = true
    override val reply = true

    override suspend fun execute(update: Update, message: Message): String {
        val bot = getBotOrThrow()
        val user = if (message.isReply()) {
            if (!bot.userConfig.superAdmins.contains(message.from?.id)) return "只允许超级管理员管理他人信息"
            if (message.replyToMessage?.from?.username == bot.tg.username) {
                val qqMsg = CacheService.getQQByTg(message.replyToMessage!!) ?: return "找不到qq信息"
                val user = bot.userConfig.links.firstOrNull { it.qq == qqMsg.source.fromId } ?: return "该qq没有和tg建立链接关系"
                bot.userConfig.unlink(user)
                user
            } else {
                val user = bot.userConfig.links.firstOrNull { it.tg == message.replyToMessage?.from?.id }
                    ?: return "该用户没有和qq建立链接关系"
                bot.userConfig.unlink(user)
                user
            }
        } else {
            val user = bot.userConfig.links.firstOrNull { it.tg == message.from?.id } ?: return "未和qq建立链接关系"
            bot.userConfig.unlink(user)
            user
        }
        return "已取消qq[${user.qq}]和@${user.username}的链接关系"
    }
}

class AcceptLinkCommand : AbstractQQCommand() {

    override suspend fun execute(event: MessageEvent): Int {
        if (event.subject is Group) {
            val cache = LinkCommand.linkCache
            val reply = event.message[QuoteReply.Key] ?: return 0
            val (qq, msgList) = reply.let { cache.getIfPresent(it.source.ids[0]) } ?: return 0
            if (qq != event.sender.id) {
                event.subject.sendMessage(event.message.quote().plus("非绑定目标QQ"))
                return 1
            }
            if (event.message.filterIsInstance(PlainText::class.java).any { it.content.contains("accept") }) {
                getBotOrThrow().userConfig.link(msgList[0].from!!.id, qq, msgList[0].from!!.username!!)
                cache.invalidate(reply.source.ids[0])
                event.subject.sendMessage(event.message.quote().plus("绑定成功"))
                EditMessageText("绑定成功").apply {
                    chatId = msgList[1].chatId
                    messageId = msgList[1].messageId
                }.send()
                return 1
            }
        }
        return 0
    }

}