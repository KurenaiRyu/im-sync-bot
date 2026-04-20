package kurenai.imsyncbot.bot.discord

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.commands.upsertCommand
import dev.minn.jda.ktx.jdabuilder.light
import dev.minn.jda.ktx.messages.EmbedBuilder
import dev.minn.jda.ktx.messages.MessageEditBuilder
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.domain.QQDiscord
import kurenai.imsyncbot.domain.by
import kurenai.imsyncbot.domain.copy
import kurenai.imsyncbot.repository.GroupConfigRepository
import kurenai.imsyncbot.repository.QQDiscordRepository
import kurenai.imsyncbot.repository.QQMessageRepository
import kurenai.imsyncbot.repository.UserConfigRepository
import kurenai.imsyncbot.snowFlake
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.BotUtil.toMessageChain
import kurenai.imsyncbot.utils.HttpUtil
import kurenai.imsyncbot.utils.fs
import kurenai.imsyncbot.utils.getLogger
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.utils.FileUpload
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.contact.remarkOrNameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import org.babyfish.jimmer.kt.new
import top.mrxiaom.overflow.message.data.withUrl
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


/**
 * @author Kurenai
 * @since 2023/6/18 18:03
 */

class DiscordBot(
    val bot: ImSyncBot,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : CoroutineScope {

    companion object {
        val log = getLogger()
        const val IMAGE_SIZE: Int = 10 * 1024 * 1024 - 100
    }

    lateinit var jda: JDA
    val incomingMessageChannel: Channel<GroupAwareMessageEvent> = Channel(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val incomingEventChannel: Channel<GroupEvent> = Channel(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    private val syncMessageChannel: Channel<OnlineMessageSource.Outgoing> =
        Channel(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    override val coroutineContext: CoroutineContext = SupervisorJob(coroutineContext[Job]) +
            Dispatchers.Default +
            CoroutineName("DiscordBot") +
            CoroutineExceptionHandler { context, exception ->
                when (exception) {
                    is CancellationException -> {
                        log.warn("{} was cancelled", context[CoroutineName])
                    }

                    else -> {
                        log.warn("with {}", context[CoroutineName], exception)
                    }
                }
            }

    fun start() {
        val token = bot.configProperties.bot.discord.token
        if (token == null) {
            coroutineContext.cancel(CancellationException("Discord token is null"))
            return
        }
        jda = light(token, true) {
            this.setAutoReconnect(true)
        }

        launch {
            jda.awaitReady()
            log.info("JDA Ready")
            initChannel()
        }
    }

    private fun initChannel() {
        val guild = jda.guilds.firstOrNull() ?: return
        guild.upsertCommand("bind", "Bind qq group") {
            addOption(OptionType.INTEGER, "group_id", "qq group id", true)
            addOption(
                OptionType.BOOLEAN,
                "new_channel",
                "Pass true to generate a channel for bind group, false default",
                true
            )
        }

        jda.listener<GenericEvent> {
            log.trace("Received a generic event: {}", it)
        }

        jda.onCommand("bind") {
            handleBindCommand(it)
        }

        bot.qq.qqBot.eventChannel.subscribeAlways<Event> { event ->
            when (event) {
                is GroupAwareMessageEvent -> incomingMessageChannel.trySend(event)
                is GroupMessagePostSendEvent -> {
                    event.receipt?.source?.let { syncMessageChannel.trySend(it) }
                }
                is GroupTempMessagePostSendEvent -> {
                    event.receipt?.source?.let { syncMessageChannel.trySend(it) }
                }
                is GroupEvent -> incomingEventChannel.trySend(event)

                else -> {}
            }
        }

        launch {
            for (source in syncMessageChannel) {
                runCatching {
                    handleSyncMessage(source)
                }.onFailure {
                    log.error("Handle sync message error", it)
                }
            }
        }
        launch {
            for (event in incomingMessageChannel) {
                runCatching {
                    handleGroupMessage(event)
                }.onFailure {
                    log.error("Handle group message error", it)
                }
            }
        }
        launch {
            for (event in incomingEventChannel) {
                runCatching {
                    handleGroupEvent(event)
                }.onFailure {
                    log.error("Handle group message error", it)
                }
            }
        }
    }

    private suspend fun handleBindCommand(event: GenericCommandInteractionEvent) {
        val params = event.options.associateBy { it.name }
        val groupId = params["group_id"]?.asLong ?: return
        val enabledNewChannel = params["new_channel"]?.asBoolean ?: return
        val group = bot.qq.qqBot.getGroup(groupId)

        if (group != null) {
            val channel = if (enabledNewChannel) {
                val category =
                    event.guild!!.categories.first { it.name == "forward" } ?: run {
                        event.guild!!.createCategory("forward").await()
                    }
                category.createTextChannel(group.name).await()
            } else event.channel!!


            val config = GroupConfigRepository.findByQqGroupId(groupId)
            if (config != null) {
                if (config.discordChannelId != channel.idLong) {
                    GroupConfigRepository.save(config.copy {
                        discordChannelId = channel.idLong
                    })
                }
            } else {
                GroupConfigRepository.save(
                    new(GroupConfig::class).by {
                        this.qqGroupId = groupId
                        this.name = group.name
                        discordChannelId = channel.idLong
                        id = snowFlake.nextId()
                    }
                )
            }
        }

        event.reply_(
            if (group != null) {
                "Bind group ${group.name}(${group.id})"
            } else {
                "Group $groupId not found"
            },
        ).queue()
    }

//    suspend fun resolveMissChannel(groupConfigs: List<GroupConfig>) {
//        val missConfigs = if (groupConfigs.isEmpty()) {
//            bot.groupConfigService.configs.map {
//                GroupConfig(it.qqGroupId, it.name, it.telegramGroupId, status = it.status.joinToString(","), id = snowFlake.nextId())
//            }
//        } else {
//            groupConfigs.filter { it.discordChannelId == null }
//        }
//
//        val guild = kord.guilds.firstOrNull() ?: return
//        val category =
//            guild.channels.firstOrNull { it is Category && it.name == "forward" } as? Category
//                ?: guild.createCategory("forward")
//        val existChannel = category.channels.toList().associateBy { it.name }
//
//        missConfigs.forEach { config ->
//            val channel = existChannel[config.name] ?: category.createTextChannel(config.name)
//            config.discordChannelId = channel.idLong
//        }
//        GroupConfigRepository.saveAll(missConfigs)
//    }

    private suspend fun handleSyncMessage(source: OnlineMessageSource.Outgoing) {
        val channelId = GroupConfigRepository.findByQqGroupId(source.target.id)?.discordChannelId ?: return
        val channel = jda.getChannel<TextChannel>(channelId) ?: return
        val webhook =
            channel.retrieveWebhooks().await().firstOrNull { it.name == "forward" } ?: channel.createWebhook("forward")
                .await()
        val name = "${source.sender.nameCardOrNick} #${source.sender.id}"
        val avatarUrl = source.sender.avatarUrl
        val group = source.target as? Group ?: return

        with(MessageContext(source.originalMessage, webhook, name, avatarUrl, group)) {
            handleMessage()
        }

    }

    suspend fun handleGroupMessage(event: GroupAwareMessageEvent) {
        val group = event.group
        val channelId = GroupConfigRepository.findByQqGroupId(group.id)?.discordChannelId ?: return
        val channel = jda.getChannel<TextChannel>(channelId) ?: return
        val webhook =
            channel.retrieveWebhooks().await().firstOrNull { it.name == "forward" } ?: channel.createWebhook("forward")
                .await()
        val name =
            "${bot.userConfigService.idBindings[event.sender.id] ?: group[event.sender.id]?.remarkOrNameCardOrNick ?: event.sender.remark.takeIf { it.isNotBlank() } ?: event.senderName} #${event.sender.id}"
        val avatarUrl = event.sender.avatarUrl
        with(MessageContext(event.message, webhook, name, avatarUrl, group)) {
            handleMessage()
        }
    }

    suspend fun handleGroupEvent(event: GroupEvent) {
        val group = event.group
        val channelId = GroupConfigRepository.findByQqGroupId(group.id)?.discordChannelId ?: return
        val channel = jda.getChannel<TextChannel>(channelId) ?: return
        val webhook =
            channel.retrieveWebhooks().await().firstOrNull { it.name == "forward" } ?: channel.createWebhook("forward")
                .await()

        val msg = when (event) {
            is MessageRecallEvent.GroupRecall -> {
                val infos = QQDiscordRepository.findByQQ(event.messageIds[0], event.group.id)?:return
                for (info in infos) {
                    val origin = webhook.retrieveMessageById(info.messageId.toString()).await()
                    if (origin.contentStripped.isBlank()) {
                        origin.addReaction(Emoji.fromUnicode("\uD83D\uDDD1")) // 🗑
                            .await()
                    } else {
                        val content = StringBuilder("")
                        origin.contentRaw.replace("^~~|~~&", "")
                            .lines().forEach { content.appendLine("~~$it~~") }
                        val msg = MessageEditBuilder(content.toString()).build()
                        webhook.editMessageById(info.messageId, msg)
                            .await()
                    }
                }
                return
            }

            is MemberJoinEvent -> {
                when (event) {
                    is MemberJoinEvent.Active -> {
                        "${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick)}#${event.member.id} 入群 ${event.group.name} "
                    }

                    is MemberJoinEvent.Invite -> {
                        "${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick)}#${event.member.id} 通过 ${(bot.userConfigService.idBindings[event.invitor.id] ?: event.invitor.remarkOrNameCardOrNick)}#${event.invitor.id} 的邀请入群"
                    }

                    else -> return
                }
            }

            is MemberLeaveEvent.Kick -> {
                "${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick)}#${event.member.id} 被踢出群"
            }

            is MemberLeaveEvent.Quit -> {
                "${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick)}#${event.member.id} 退出群"
            }


            is MemberMuteEvent -> {
                "${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick)}#${event.member.id} 被禁言${event.durationSeconds / 60}分钟"
            }

            is GroupMuteAllEvent -> {
                "${(bot.userConfigService.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick) ?: "?"}#${event.operator?.id?:"?"} 禁言了所有人"
            }

            is MemberUnmuteEvent -> {
                "${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick)}#${event.member.id} 被 ${(bot.userConfigService.idBindings[event.operator?.id] ?: event.operator?.remarkOrNameCardOrNick) ?: "?"}#${event.operator?.id ?: "?"} 解除禁言"
            }

            is MemberCardChangeEvent -> {
                if (event.new.isNotEmpty()) {
                    "${(bot.userConfigService.idBindings[event.member.id] ?: event.origin)}#${event.member.id} 名称改为 ${event.new} "
                } else {
                    return
                }
            }

            is MemberSpecialTitleChangeEvent -> {
                "`${(bot.userConfigService.idBindings[event.member.id] ?: event.member.remarkOrNameCardOrNick)}#${event.member.id} 获得头衔 ${event.new}`"
            }

            else -> {
                log.debug("未支持群事件 {} 的处理", event.javaClass)
                return
            }
        }

        val name = "Group Event"
        val avatarUrl = group.avatarUrl

        webhook.sendMessage(msg)
            .setUsername(name)
            .setAvatarUrl(avatarUrl)
            .await()
    }

    context(ctx: MessageContext)
    suspend fun handleMessage() = ctx.apply {
        for ((index, message) in messageChain.withIndex()) {
            when (message) {
                is MessageSource -> {
                    continue
                }
                is FlashImage, is Image -> {
                    val image = when (message) {
                        is FlashImage -> message.image
                        is Image -> message
                        else -> continue
                    }

                    val url = image.queryUrl()
                    var path = BotUtil.downloadImg(url = url, filename = image.imageId)
                    var size = (fs.metadataOrNull(path)?.size ?: 0)
                    if (size >= IMAGE_SIZE) {
                        path = BotUtil.toWebp(path)
                        size = (fs.metadataOrNull(path)?.size ?: 0)
                    }

                    if (size > IMAGE_SIZE) {
                        doError("File size is too large")
                    }

                    if (files.isNotEmpty() || images.size >= 10)
                        doSendMessage()

                    images.add(FileUpload.fromData(path.toNioPath()))
                }

                is FileMessage -> {

                    val url = message.withUrl.url
                    if (HttpUtil.getRemoteFileSize(url) >= IMAGE_SIZE) doError("File size is too large")

                    val path = BotUtil.downloadDoc(url = url, filename = message.name)

                    if (images.isNotEmpty() || files.size >= 10)
                        doSendMessage()

                    files.add(FileUpload.fromData(path.toNioPath()))
                }

                is OnlineShortVideo -> {
                    val size = message.fileSize
                    if (size > IMAGE_SIZE) {
                        doError("Video size is too large")
                    }

                    val path = BotUtil.downloadDoc("${message.filename}.${message.fileFormat}", message.urlForDownload)
                    files.add(FileUpload.fromData(path.toNioPath()))
                }

                is QuoteReply -> {
                    val source = message.source
                    val sourceMsg = QQMessageRepository
                        .findByBotIdAndTargetIdAndMessageId(source.botId, source.targetId, source.ids[0])
                        ?.toMessageChain()
                        ?: continue
                    val replyInfo = QQDiscordRepository.findByQQ(source.ids[0], group.id).firstOrNull()
                    val member = group[source.fromId]

                    embeds.add(EmbedBuilder {
                        description = sourceMsg.content.takeIf { it.isNotBlank() } ?: "No things"
                        author {
                            iconUrl = member?.avatarUrl
                            name = "${member?.remarkOrNameCardOrNick ?: "???"} #${member?.id ?: 0}"
                            //https://discord.com/channels/804632979057410068/1494027438555009027/1495419126800453822
                            if (replyInfo != null) {
                                url = "https://discord.com/channels/${replyInfo.guildId}/${replyInfo.channelId}/${replyInfo.messageId}}"
                            }
                        }
                    }.build())
                }

                else -> {
                    if (files.isNotEmpty() || images.isNotEmpty() || embeds.isNotEmpty()) doSendMessage()

                    val content = when (message) {
                        is At -> {
                            if (messageChain[(index - 1).coerceAtLeast(0)] is QuoteReply) continue

                            val id = message.target
                            val name =
                                UserConfigRepository.findByQQ(id)?.bindingName ?: group[id]?.remarkOrNameCardOrNick
                                ?: "?"
                            "@${name} #${id}"
                        }

                        else -> {
                            log.debug("${message::class.simpleName}: ${message.contentToString()}")
                            message.contentToString()
                        }
                    }

                    if (content.isBlank()) continue

                    this.content.append(content)
                }
            }
        }
        doSendMessage()
    }

    context(ctx: MessageContext)
    private suspend fun doError(string: String): String {
        doSendMessage()
        error(string)
    }

    context(ctx: MessageContext)
    private suspend fun doSendMessage() = ctx.apply {
        if (content.isBlank() && files.isEmpty() && images.isEmpty() && embeds.isEmpty()) return@apply

        val action = webhook.sendMessage(content.toString())
            .setUsername(senderName)
            .setAvatarUrl(avatarUrl)
        if (files.isNotEmpty()) action.setFiles(files)
        if (images.isNotEmpty()) action.setFiles(images)
        if (embeds.isNotEmpty()) action.setEmbeds(embeds)

        try {
            val res = action.await()
            QQDiscordRepository.save(new(QQDiscord::class).by {
                qqMsgId = messageChain.source.ids[0]
                qqGroupId = messageChain.source.targetId
                guildId = res.guildIdLong
                channelId = res.channelIdLong
                messageId = res.idLong
            })
        } finally {
            content.setLength(0)
            files.clear()
            images.clear()
            embeds.clear()
        }
    }

}