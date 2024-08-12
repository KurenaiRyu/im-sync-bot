//package kurenai.imsyncbot.command.impl
//
//import it.tdlight.jni.TdApi
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kurenai.imsyncbot.ImSyncBot
//import kurenai.imsyncbot.command.AbstractQQCommand
//import kurenai.imsyncbot.command.AbstractTelegramCommand
//import kurenai.imsyncbot.command.Bannable
//import kurenai.imsyncbot.getBotOrThrow
//import kurenai.imsyncbot.service.CacheService
//import kurenai.imsyncbot.telegram.send
//import moe.kurenai.tdlight.model.ParseMode
//import moe.kurenai.tdlight.model.message.Message
//import moe.kurenai.tdlight.model.message.User
//import moe.kurenai.tdlight.request.message.SendMessage
//import moe.kurenai.tdlight.util.MarkdownUtil.fm2md
//import net.mamoe.mirai.contact.*
//import net.mamoe.mirai.event.events.MessageEvent
//import net.mamoe.mirai.message.data.source
//import org.apache.logging.log4j.LogManager
//
//class TitleCommand : AbstractTelegramCommand(), Bannable {
//
//    private val log = LogManager.getLogger()
//
//    override val command = "title"
//    override val help: String = "修改自己或他人qq头衔"
//    override val onlySupperAdmin = false
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String? {
//        val bot = getBotOrThrow()
//        val param = message.text!!.param()
//        if (message.isReply()) {
//            val replyMsg = message.replyToMessage!!
//            try {
//                val from = replyMsg.from!!
//                bot.userConfig.superAdmins.firstOrNull { message.from?.id == it }?.also {
//                    if (!handleLinkCase(bot, from, message, param)) {
//                        val qqMsg = CacheService.getQQByTg(replyMsg)
//                        if (qqMsg == null) {
//                            SendMessage(message.chatId, "未能找到对应的qq消息").apply {
//                                replyToMessageId = message.messageId
//                            }.send()
//                        } else {
//                            bot.qq.qqBot.getGroup(qqMsg.source.targetId)?.getMember(qqMsg.source.fromId)?.also {
//                                modifyTitle(it, message, param)
//                            } ?: kotlin.run {
//                                SendMessage(message.chatId, "未能找到对应的qq用户或群组").apply {
//                                    replyToMessageId = message.messageId
//                                }.send()
//                            }
//                        }
//                    }
//                } ?: kotlin.run {
//                    SendMessage(message.chatId, "只有超级管理员能够修改他人qq头衔").apply {
//                        replyToMessageId = message.messageId
//                    }.send()
//                }
//            } catch (e: Exception) {
//                log.error(e.message, e)
//                "error: ${e.message}"
//            }
//        } else {
//            val from = message.from!!
//            if (!handleLinkCase(bot, from, message, param)) {
//                SendMessage(message.chatId, "未能找到对应的qq用户").apply {
//                    this.replyToMessageId = message.messageId
//                }.send()
//            }
//        }
//        return null
//    }
//
//    private fun handleLinkCase(bot: ImSyncBot, target: User, message: Message, modifyTitle: String): Boolean {
//        return bot.userConfig.links.firstOrNull { it.tg == target.id && it.qq != null }?.also { user ->
//            CoroutineScope(Dispatchers.Default).launch {
//                bot.groupConfig.tgQQ[message.chat.id]?.let { bot.qq.qqBot.getGroup(it) }?.getMember(user.qq!!)?.also {
//                    modifyTitle(it, message, modifyTitle)
//                }
//            }
//        } != null
//    }
//
//    private suspend fun modifyTitle(member: NormalMember, message: Message, modifyTitle: String) {
//        try {
//            member.specialTitle = modifyTitle
//            SendMessage(
//                message.chatId,
//                "qq`${member.remarkOrNameCardOrNick.fm2md()}`的头衔已修改为`${modifyTitle.fm2md()}`"
//            ).apply {
//                this.replyToMessageId = message.messageId
//                this.parseMode = ParseMode.MARKDOWN_V2
//            }.send()
//        } catch (e: PermissionDeniedException) {
//            SendMessage(message.chatId, "bot没有权限修改qq头衔").apply {
//                this.replyToMessageId = message.messageId
//            }.send()
//        }
//    }
//
//}
//
//class TitleQQCommand : AbstractQQCommand() {
//
//    override suspend fun execute(event: MessageEvent): Int {
//        val text = event.message.contentToString()
//        return if (text.startsWith("头衔")) {
//            val modifyTitle = text.substring("头衔".length).trim()
//            val group = event.subject as Group
//            modifyTitle(group, event, modifyTitle)
//            1
//        } else {
//            0
//        }
//    }
//
//    private suspend fun modifyTitle(group: Group, event: MessageEvent, modifyTitle: String) {
//        CoroutineScope(Dispatchers.Default).launch {
//            group.getMember(event.sender.id)?.let {
//                try {
//                    it.specialTitle = modifyTitle
//                    event.subject.sendMessage("[${it.nameCardOrNick}]头衔已修改为[$modifyTitle]")
//                } catch (e: PermissionDeniedException) {
//                    event.subject.sendMessage("bot无权修改头衔")
//                }
//            } ?: kotlin.run {
//                event.subject.sendMessage("找不到用户[${event.sender.id}]")
//            }
//        }
//    }
//}