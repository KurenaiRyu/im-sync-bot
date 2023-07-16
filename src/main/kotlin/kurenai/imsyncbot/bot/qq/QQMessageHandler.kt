package kurenai.imsyncbot.bot.qq

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import it.tdlight.jni.TdApi.EditMessageCaption
import it.tdlight.jni.TdApi.EditMessageText
import it.tdlight.jni.TdApi.InputMessageText
import it.tdlight.jni.TdApi.MessageText
import it.tdlight.jni.TdApi.TextEntity
import it.tdlight.jni.TdApi.TextEntityTypeStrikethrough
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.service.MessageService
import kurenai.imsyncbot.utils.TelegramUtil.textOrCaption
import kurenai.imsyncbot.utils.TelegramUtil.escapeMarkdownChar
import kurenai.imsyncbot.utils.TelegramUtil.fmt
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
    private val mapper = jacksonObjectMapper()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)

    init {
        if (configProperties.bot.tgMsgFormat.contains("\$msg")) tgMsgFormat = configProperties.bot.tgMsgFormat
        if (configProperties.bot.qqMsgFormat.contains("\$msg")) qqMsgFormat = configProperties.bot.qqMsgFormat
    }

    @Throws(Exception::class)
    override suspend fun onGroupMessage(context: GroupMessageContext): Int {
        if (context.bot.groupConfigService.bannedGroups.contains(context.group.id)) return CONTINUE
        val messageType = context.getReadyToSendMessage()
        val list = if (messageType is GroupMessageContext.Forward) messageType.contextList else listOf(context)
        for (resolvedContext in list) {
            val readyToSendMessage = resolvedContext.getReadyToSendMessage()
            kotlin.runCatching {
                readyToSendMessage.send()
            }.recoverCatching {
                resolvedContext.normalType.send()
            }.getOrThrow().also { messages ->
                log.debug("${context.infoString} Sent ${mapper.writeValueAsString(messages)}")
                if (context.entity == null) return@also
                CoroutineScope(bot.coroutineContext).launch {
                    MessageService.cache(context.entity, context.messageChain, messages)
                }
            }
        }
        return CONTINUE
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
                                this.text = content.text
                            }
                        }
                    } else {
                        EditMessageCaption().apply {
                            this.chatId = originMsg.chatId
                            this.messageId = originMsg.id
                            val caption = content.textOrCaption()
                            this.caption = caption?.apply {
                                val size = this.entities.size + 1
                                Array<TextEntity>(size) {
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
        val msg = when (event) {
            is MemberJoinEvent -> {
                val tag = "\\#入群 \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberJoinEvent.Active -> {
                        "$tag`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdownChar()}`入群`${event.group.name}`"
                    }

                    is MemberJoinEvent.Invite -> {
                        "$tag`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdownChar()}`通过`${(bot.userConfig.idBindings[event.invitor.id] ?: event.invitor.remarkOrNameCardOrNick).escapeMarkdownChar()}` \\#id${event.invitor.id}\\ 的邀请入群`${event.group.name}`"
                    }

                    else -> return
                }
            }

            is MemberLeaveEvent -> {
                val tag = "\\#退群 \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberLeaveEvent.Kick -> {
                        "$tag`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdownChar()}`被踢出群`${event.group.name}`"
                    }

                    is MemberLeaveEvent.Quit -> {
                        "$tag`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdownChar()}`退出群`${event.group.name}`"
                    }

                    else -> return
                }
            }

            is MemberMuteEvent -> {
                "\\#禁言\n\\#id${event.member.id}\n`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdownChar()}`被禁言${event.durationSeconds / 60}分钟"
            }

            is GroupMuteAllEvent -> {
                "\\#禁言\n`${(bot.userConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.escapeMarkdownChar() ?: "?"}` \\#id${event.operator?.id ?: "?"} 禁言了所有人"
            }

            is MemberUnmuteEvent -> {
                "\\#禁言\n`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdownChar()}` \\#id${event.member.id} 被`${(bot.userConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.escapeMarkdownChar() ?: "?"})` \\#id${event.operator?.id ?: "?"} 解除禁言"
            }

            is MemberCardChangeEvent -> {
                if (event.new.isNotEmpty()) {
                    "\\#名称 \\#id${event.member.id}\n`${(bot.userConfig.idBindings[event.member.id] ?: event.origin).escapeMarkdownChar()}`名称改为`${event.new.escapeMarkdownChar()}`"
                } else {
                    return
                }
            }

            is MemberSpecialTitleChangeEvent -> {
                "\\#头衔 \\#id${event.member.id}\n`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).escapeMarkdownChar()}`获得头衔`${event.new.escapeMarkdownChar()}`"
            }

            else -> {
                log.debug("未支持群事件 {} 的处理", event.javaClass)
                return
            }
        }
        val chatId = bot.groupConfigService.qqTg[event.group.id] ?: bot.groupConfigService.defaultTgGroup
        bot.tg.sendMessageText(msg.fmt(), chatId)
    }

    override fun order(): Int {
        return 150
    }

}