package kurenai.imsyncbot.bot.qq

import it.tdlight.jni.TdApi.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.TelegramUtil.escapeMarkdown
import kurenai.imsyncbot.utils.TelegramUtil.fmt
import kurenai.imsyncbot.utils.TelegramUtil.sendUserId
import kurenai.imsyncbot.utils.TelegramUtil.textOrCaption
import kurenai.imsyncbot.utils.TelegramUtil.userSender
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.contact.remarkOrNameCardOrNick
import net.mamoe.mirai.event.events.*

class QQMessageHandler(
    configProperties: ConfigProperties,
    internal val bot: ImSyncBot
) : QQHandler {

    private val log = getLogger()

    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"
    private var enableRecall = configProperties.bot.enableRecall

    init {
        if (configProperties.bot.tgMsgFormat.contains("\$msg")) tgMsgFormat = configProperties.bot.tgMsgFormat
        if (configProperties.bot.qqMsgFormat.contains("\$msg")) qqMsgFormat = configProperties.bot.qqMsgFormat
    }

    @Throws(Exception::class)
    override suspend fun onGroupMessage(context: GroupMessageContext): Int {
        if (context.bot.groupConfigService.bannedGroups.contains(context.group.id)) return CONTINUE
        val messageType = context.getReadyToSendMessage()
        val list = if (messageType is GroupMessageContext.Forward) messageType.contextList else listOf(context)
        if (list.size > 5) {
            CoroutineScope(bot.qq.coroutineContext).launch {
                sendMessage(list, context)
            }
        } else {
            sendMessage(list, context)
        }
        return CONTINUE
    }

    private suspend fun sendMessage(list: List<GroupMessageContext>, context: GroupMessageContext) {
        for (resolvedContext in list) {
            when (resolvedContext.getReadyToSendMessage()) {
                is GroupMessageContext.ShortVideo,
                is GroupMessageContext.Video,
                is GroupMessageContext.File -> CoroutineScope(bot.qq.coroutineContext).launch {
                    sendMessage(
                        resolvedContext,
                        context
                    )
                }

                else -> sendMessage(resolvedContext, context)
            }
        }
    }

    private suspend fun sendMessage(resolvedContext: GroupMessageContext, context: GroupMessageContext) {
        val readyToSendMessage = resolvedContext.getReadyToSendMessage()
        kotlin.runCatching {
            readyToSendMessage.send()
        }.recoverCatching {
            log.warn("Send group message error, try to send normal type message", it)
            resolvedContext.normalType.send()
        }.getOrThrow().also { messages ->
            log.debug("{} Sent {}", context.infoString,
                messages.joinToString(",") {
                    "[${it.chatId}] ${it.sendUserId()} ${
                        it.content.textOrCaption()?.text?.replace(
                            "\r",
                            "\\r"
                        )?.replace("\n", "\\n")
                    }"
                })
            if (context.entity == null) return@also
            CoroutineScope(bot.coroutineContext).launch {
                MessageService.cache(context.entity, context.messageChain, messages)
            }
        }
    }

//    @Throws(Exception::class)
//    override suspend fun onFriendMessage(context: PrivateMessageContext): Int {
//
//        val messageType = context.getType()
//        val list = if (messageType is PrivateMessageContext.Forward) messageType.contextList else listOf(context)
//        for (resolvedContext in list) {
//            val type = resolvedContext.getType()
//            val tg = bot.tg
//            kotlin.runCatching {
//                when (type) {
//                    is PrivateMessageContext.JsonMessage -> type.telegramMessage.send(tg)
//                    is PrivateMessageContext.GifImage -> type.getTelegramMessage().send(tg)
//                    is PrivateMessageContext.MultiImage -> kotlin.runCatching {
//                        type.getTelegramMessage().send(tg)
//                    }.recoverCatching {
//                        type.resolvedHttpUrlInvalidByModifyUrl().send(tg)
//                    }.recover {
//                        type.resolvedHttpUrlInvalidByLocalDownload().send(tg)
//                    }.getOrThrow()
//
//                    is PrivateMessageContext.XmlMessage -> type.telegramMessage.send(tg)
//                    is PrivateMessageContext.SingleImage -> kotlin.runCatching {
//                        type.getTelegramMessage().send(tg)
//                    }.recoverCatching {
//                        type.resolvedHttpUrlInvalidByModifyUrl().send(tg)
//                    }.recover {
//                        type.resolvedHttpUrlInvalidByLocalDownload().send(tg)
//                    }.getOrThrow()
//
//                    is PrivateMessageContext.Normal -> type.telegramMessage.send(tg)
//                    else -> null
//                }
//            }.recoverCatching {
//                resolvedContext.normalType.telegramMessage.send(tg)
//            }.getOrThrow()?.also { message ->
//                log.debug("${context.infoString} Sent ${mapper.writeValueAsString(message)}")
//                if (context.entity == null) return@also
//                if (message is Message) {
//                    MessageService.cache(context.entity, context.messageChain, message)
//                } else {
//                    message as List<Message>
//                    MessageService.cache(context.entity, context.messageChain, message.first())
//                }
//            }
//        }
//        return CONTINUE
//    }

    override suspend fun onRecall(event: MessageRecallEvent.GroupRecall): Int {
        MessageService.findRelationByRecall(event)?.let { message ->
            if (enableRecall) {
                bot.tg.deleteMessages(message.tgGrpId, message.tgMsgId)
            } else {
                val originMsg = bot.tg.getMessage(message.tgGrpId, message.tgMsgId)
                if (originMsg.userSender()?.userId != bot.tg.getMe().id) return CONTINUE
                bot.tg.send {
                    val content = originMsg.content
                    if (content is MessageText) {
                        EditMessageText().apply {
                            this.chatId = originMsg.chatId
                            this.messageId = originMsg.id
                            this.inputMessageContent = InputMessageText().apply {
                                this.text = content.text.apply {
                                    val size = this.entities.size + 1
                                    this.entities = Array<TextEntity>(size) {
                                        if (it == size - 1)
                                            TextEntity(0, content.text.text.length, TextEntityTypeStrikethrough())
                                        else
                                            this.entities[it]
                                    }
                                }
                            }
                        }
                    } else {
                        EditMessageCaption().apply {
                            this.chatId = originMsg.chatId
                            this.messageId = originMsg.id
                            val caption = content.textOrCaption()
                            this.caption = caption?.apply {
                                val size = this.entities.size + 1
                                this.entities = Array<TextEntity>(size) {
                                    if (it == size - 1)
                                        TextEntity(0, caption.text.length, TextEntityTypeStrikethrough())
                                    else
                                        this.entities[it]
                                }
                            }
                        }
                    }
                }
            }
        }
        return CONTINUE
    }

    suspend fun onGroupEvent(event: GroupEvent) {
        val chatId = bot.groupConfigService.qqTg[event.group.id] ?: return
        val msg = when (event) {
            is MemberJoinEvent -> {
                val tag = "\\#入群 \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberJoinEvent.Active -> {
                        "$tag`${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdown()}`入群`${event.group.name}`"
                    }

                    is MemberJoinEvent.Invite -> {
                        "$tag`${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdown()}`通过`${(bot.userConfigService.idBindings[event.invitor.id] ?: event.invitor.remarkOrNameCardOrNick).escapeMarkdown()}` \\#id${event.invitor.id}\\ 的邀请入群`${event.group.name}`"
                    }

                    else -> return
                }
            }

            is MemberLeaveEvent -> {
                val tag = "\\#退群 \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberLeaveEvent.Kick -> {
                        "$tag`${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdown()}`被踢出群`${event.group.name}`"
                    }

                    is MemberLeaveEvent.Quit -> {
                        "$tag`${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdown()}`退出群`${event.group.name}`"
                    }

                    else -> return
                }
            }

            is MemberMuteEvent -> {
                "\\#禁言\n\\#id${event.member.id}\n`${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdown()}`被禁言${event.durationSeconds / 60}分钟"
            }

            is GroupMuteAllEvent -> {
                "\\#禁言\n`${(bot.userConfigService.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.escapeMarkdown() ?: "?"}` \\#id${event.operator?.id ?: "?"} 禁言了所有人"
            }

            is MemberUnmuteEvent -> {
                "\\#禁言\n`${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdown()}` \\#id${event.member.id} 被`${(bot.userConfigService.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.escapeMarkdown() ?: "?"})` \\#id${event.operator?.id ?: "?"} 解除禁言"
            }

            is MemberCardChangeEvent -> {
                if (event.new.isNotEmpty()) {
                    "\\#名称 \\#id${event.member.id}\n`${(bot.userConfigService.idBindings[event.member.id] ?: event.origin).escapeMarkdown()}`名称改为`${event.new.escapeMarkdown()}`"
                } else {
                    return
                }
            }

            is MemberSpecialTitleChangeEvent -> {
                "\\#头衔 \\#id${event.member.id}\n`${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdown()}`获得头衔`${event.new.escapeMarkdown()}`"
            }

            else -> {
                log.debug("未支持群事件 {} 的处理", event.javaClass)
                return
            }
        }
        bot.tg.sendMessageText(msg.fmt(), chatId)
    }

    override fun order(): Int {
        return 150
    }

}