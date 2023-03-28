package kurenai.imsyncbot.handler.qq

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import kurenai.imsyncbot.utils.MarkdownUtil.format2Markdown
import kurenai.imsyncbot.utils.groupInfoString
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

class QQMessageHandler(
    configProperties: ConfigProperties,
    internal val bot: ImSyncBot
) : QQHandler {

    private val log = LogManager.getLogger()

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
    @Suppress("UNCHECKED_CAST")
    override suspend fun onGroupMessage(context: GroupMessageContext): Int {
        if (context.bot.groupConfig.bannedGroups.contains(context.group.id)) return CONTINUE

        val messageType = context.getType()
        val list = if (messageType is GroupMessageContext.Forward) messageType.contextList else listOf(context)
        for (resolvedContext in list) {
            val type = resolvedContext.getType()
            val tg = bot.tg
            kotlin.runCatching {
                when (type) {
                    is GroupMessageContext.JsonMessage -> type.telegramMessage.send(tg)
                    is GroupMessageContext.GifImage -> type.getTelegramMessage().send(tg)
                    is GroupMessageContext.MultiImage -> kotlin.runCatching {
                        type.getTelegramMessage().send(tg)
                    }.recover {
                        type.resolvedHttpUrlInvalid().send(tg)
                    }.getOrThrow()

                    is GroupMessageContext.XmlMessage -> type.telegramMessage.send(tg)
                    is GroupMessageContext.SingleImage -> kotlin.runCatching {
                        type.getTelegramMessage().send(tg)
                    }.recover {
                        type.resolvedHttpUrlInvalid().send(tg)
                    }.getOrThrow()

                    is GroupMessageContext.File -> if (type.shouldBeFile) type.getFileMessage().send(tg) else type.getTextMessage().send(tg)
                    is GroupMessageContext.Video -> type.getTelegramMessage().send(tg)
                    is GroupMessageContext.Normal -> type.telegramMessage.send(tg)
                    else -> null
                }
            }.recoverCatching {
                resolvedContext.normalType.telegramMessage.send(tg)
            }.getOrThrow()?.also { message ->
                log.debug("${context.groupInfoString()} Sent ${mapper.writeValueAsString(message)}")
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
                DeleteMessage(message.chatId, message.messageId!!).send(bot.tg)
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
                    }.send(bot.tg)
                } else {
                    EditMessageText(text).apply {
                        chatId = message.chatId
                        messageId = message.messageId
                        entities = mutableListOf(MessageEntity(MessageEntityType.STRIKETHROUGH, 0, text.length)).also { list ->
                            message.entities?.let { list.addAll(it) }
                        }
                    }.send(bot.tg)
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
                        "$tag`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`入群`${event.group.name}`"
                    }

                    is MemberJoinEvent.Invite -> {
                        "$tag`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`通过`${(bot.userConfig.idBindings[event.invitor.id] ?: event.invitor.remarkOrNameCardOrNick).format2Markdown()}` \\#id${event.invitor.id}\\ 的邀请入群`${event.group.name}`"
                    }

                    else -> return
                }
            }

            is MemberLeaveEvent -> {
                val tag = "\\#退群 \\#id${event.member.id} \\#group${event.group.id}\n"
                when (event) {
                    is MemberLeaveEvent.Kick -> {
                        "$tag`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`被踢出群`${event.group.name}`"
                    }

                    is MemberLeaveEvent.Quit -> {
                        "$tag`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`退出群`${event.group.name}`"
                    }

                    else -> return
                }
            }

            is MemberMuteEvent -> {
                "\\#禁言\n\\#id${event.member.id}\n`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`被禁言${event.durationSeconds / 60}分钟"
            }

            is GroupMuteAllEvent -> {
                "\\#禁言\n`${(bot.userConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.format2Markdown() ?: "?"}` \\#id${event.operator?.id ?: "?"} 禁言了所有人"
            }

            is MemberUnmuteEvent -> {
                "\\#禁言\n`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}` \\#id${event.member.id} 被`${(bot.userConfig.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick)?.format2Markdown() ?: "?"})` \\#id${event.operator?.id ?: "?"} 解除禁言"
            }

            is MemberCardChangeEvent -> {
                if (event.new.isNotEmpty()) {
                    "\\#名称 \\#id${event.member.id}\n`${(bot.userConfig.idBindings[event.member.id] ?: event.origin).format2Markdown()}`名称改为`${event.new.format2Markdown()}`"
                } else {
                    return
                }
            }

            is MemberSpecialTitleChangeEvent -> {
                "\\#头衔 \\#id${event.member.id}\n`${(bot.userConfig.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick).format2Markdown()}`获得头衔`${event.new.format2Markdown()}`"
            }

            else -> {
                log.debug("未支持群事件 ${event.javaClass} 的处理")
                return
            }
        }
        val chatId = bot.groupConfig.qqTg[event.group.id] ?: bot.groupConfig.defaultTgGroup
        SendMessage(chatId.toString(), msg).apply { parseMode = ParseMode.MARKDOWN_V2 }.send(bot.tg)
    }

    override fun order(): Int {
        return 150
    }

}