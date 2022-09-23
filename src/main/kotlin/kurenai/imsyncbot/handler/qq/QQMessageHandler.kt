package kurenai.imsyncbot.handler.qq

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.GroupConfig.qqTg
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.configProperties
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import moe.kurenai.tdlight.exception.TelegramApiException
import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.MessageEntity
import moe.kurenai.tdlight.request.message.DeleteMessage
import moe.kurenai.tdlight.request.message.EditMessageCaption
import moe.kurenai.tdlight.request.message.EditMessageText
import moe.kurenai.tdlight.request.message.SendMessage
import net.mamoe.mirai.contact.remarkOrNameCardOrNick
import net.mamoe.mirai.event.events.*
import org.apache.logging.log4j.LogManager

class QQMessageHandler : QQHandler {

    private val log = LogManager.getLogger()

    private var tgMsgFormat = "\$name: \$msg"
    private var qqMsgFormat = "\$name: \$msg"
    private var enableRecall = configProperties.handler.enableRecall
    private val mapper = jacksonObjectMapper()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)

    init {
        if (configProperties.handler.tgMsgFormat.contains("\$msg")) tgMsgFormat = configProperties.handler.tgMsgFormat
        if (configProperties.handler.qqMsgFormat.contains("\$msg")) qqMsgFormat = configProperties.handler.qqMsgFormat
    }

    @Throws(Exception::class)
    @Suppress("UNCHECKED_CAST")
    override suspend fun onGroupMessage(context: GroupMessageContext): Int {
        val messageType = context.getType()
        val list = if (messageType is GroupMessageContext.Forward) messageType.contextList else listOf(context)
        for (c in list) {
            val t = c.getType()
            kotlin.runCatching {
                when (t) {
                    is GroupMessageContext.App -> t.telegramMessage.send()
                    is GroupMessageContext.GifImage -> t.getTelegramMessage().send()
                    is GroupMessageContext.MultiImage -> t.getTelegramMessage().send()
                    is GroupMessageContext.Rich -> t.telegramMessage.send()
                    is GroupMessageContext.SingleImage -> if (t.shouldBeFile) t.getFileMessage().send() else t.getImageMessage().send()
                    is GroupMessageContext.Normal -> t.telegramMessage.send()
                    else -> null
                }
            }.recoverCatching {
                c.normalType.telegramMessage.send()
            }.getOrThrow()?.also { message ->
                log.debug("Sent ${mapper.writeValueAsString(message)}")
                if (message is Message) {
                    CacheService.cache(context.messageChain, message)
                } else {
                    message as List<Message>
                    CacheService.cache(context.messageChain, message.first())
                }
            }
        }
        return CONTINUE
    }

    @Throws(TelegramApiException::class)
    override suspend fun onRecall(event: MessageRecallEvent.GroupRecall): Int {
        CacheService.getTgByQQ(event.group.id, event.messageIds[0])?.let { message ->
            if (enableRecall) {
                DeleteMessage(message.chatId, message.messageId!!).send()
            } else {
                val text = message.text
                if (text.isNullOrBlank()) {
                    EditMessageCaption().apply {
                        chatId = message.chatId
                        messageId = message.messageId
                        caption = message.caption
                        caption?.let { c ->
                            captionEntities = mutableListOf(MessageEntity(MessageEntityType.STRIKETHROUGH, 0, c.length)).also { list ->
                                message.captionEntities?.let { list.addAll(it) }
                            }
                        }
                    }.send()
                } else {
                    EditMessageText(text).apply {
                        chatId = message.chatId
                        messageId = message.messageId
                        entities = mutableListOf(MessageEntity(MessageEntityType.STRIKETHROUGH, 0, text.length)).also { list ->
                            message.entities?.let { list.addAll(it) }
                        }
                    }.send()
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
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`入群`${event.group.name}`"
                    }

                    is MemberJoinEvent.Invite -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`通过`${(UserConfig.idBindings[event.invitor.id] ?: event.invitor.remarkOrNameCardOrNick).format2Markdown()}` \\#id${event.invitor.id}\\ 的邀请入群`${event.group.name}`"
                    }

                    else -> return
                }
            }

            is MemberLeaveEvent -> {
                val tag = "\\#退群 \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberLeaveEvent.Kick -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`被踢出群`${event.group.name}`"
                    }

                    is MemberLeaveEvent.Quit -> {
                        "$tag`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`退出群`${event.group.name}`"
                    }

                    else -> return
                }
            }

            is MemberMuteEvent -> {
                "\\#禁言\n\\#id${event.member.id}\n`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`被禁言${event.durationSeconds / 60}分钟"
            }

            is GroupMuteAllEvent -> {
                "\\#禁言\n`${(UserConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.format2Markdown() ?: "?"}` \\#id${event.operator?.id ?: "?"} 禁言了所有人"
            }

            is MemberUnmuteEvent -> {
                "\\#禁言\n`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}` \\#id${event.member.id} 被`${(UserConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.format2Markdown() ?: "?"})` \\#id${event.operator?.id ?: "?"} 解除禁言"
            }

            is MemberCardChangeEvent -> {
                if (event.new.isNotEmpty()) {
                    "\\#名称 \\#id${event.member.id}\n`${(UserConfig.idBindings[event.member.id] ?: event.origin).format2Markdown()}`名称改为`${event.new.format2Markdown()}`"
                } else {
                    return
                }
            }

            is MemberSpecialTitleChangeEvent -> {
                "\\#头衔 \\#id${event.member.id}\n`${(UserConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`获得头衔`${event.new.format2Markdown()}`"
            }

            else -> {
                log.debug("未支持群事件 ${event.javaClass} 的处理")
                return
            }
        }
        val chatId = qqTg[event.group.id] ?: GroupConfig.defaultTgGroup
        SendMessage(chatId.toString(), msg).apply { parseMode = ParseMode.MARKDOWN_V2 }.send()
    }

    override fun order(): Int {
        return 150
    }

}