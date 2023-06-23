package kurenai.imsyncbot.bot.discord

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createTextChannel
import dev.kord.core.behavior.channel.createWebhook
import dev.kord.core.behavior.createCategory
import dev.kord.core.behavior.createChatInputCommand
import dev.kord.core.behavior.execute
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.reply
import dev.kord.core.entity.Webhook
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.boolean
import dev.kord.rest.builder.interaction.integer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kurenai.imsyncbot.*
import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.domain.QQDiscord
import kurenai.imsyncbot.bot.qq.QQBot
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.snowFlake
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupMessageSyncEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import java.util.WeakHashMap
import kotlin.coroutines.CoroutineContext

/**
 * @author Kurenai
 * @since 2023/6/18 18:03
 */

class DiscordBot(
    val bot: ImSyncBot,
) : CoroutineScope {

    companion object {
        val log = getLogger()
    }

    lateinit var kord: Kord
    val incomingEventChannel: Channel<Event> = Channel(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    val syncMessageChannel: Channel<OnlineMessageSource.Outgoing> =
        Channel(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
    override val coroutineContext: CoroutineContext = bot.coroutineContext + CoroutineName("DiscordBot")

    val channelCache = WeakHashMap<Long, TextChannel>()

    suspend fun start() {
        val token = bot.configProperties.bot.discord.token
        if (token == null) {
            coroutineContext.cancel(CancellationException("Discord token is null"))
            return
        }
        kord = Kord(token)
        kord.gateway.gateways
        kord.on<ReadyEvent> {
            initChannel()
        }
        coroutineScope {
            launch {
                kord.login()

                var count = 0
                while (count < 3) {
                    count++
                    delay(3000L * count)
                    runCatching {
                        kord.login()
                    }.onFailure {
                        log.error("Try relogin failed", it)
                    }
                }

                log.warn("Discord bot was logout")
            }
        }
    }

    suspend fun initChannel() {
//        val groupConfigs = groupConfigRepository.findAll()
        val guild = kord.guilds.firstOrNull() ?: return
        guild.createChatInputCommand("bind", "Bind qq group") {
            integer("group_id", "qq group id") {
                required = true
//                autocomplete = true
//                bot.qq.qqBot.bot.groups.sortedBy {
//                    it.botAsMember.lastSpeakTimestamp
//                }.takeLast(25).forEach {
//                    choice(it.name, it.id)
//                }
            }
            boolean("new_channel", "Pass true to generate a channel for bind group, false default") {
                required = true
            }
        }

        kord.on<dev.kord.core.event.Event> {
            log.trace(toString())
        }

        kord.on<GuildChatInputCommandInteractionCreateEvent> {
            val command = interaction.command
            if (command.data.name.value == "bind") {
                handleBindCommand()
            }
        }

//        kord.on<GuildAutoCompleteInteractionCreateEvent> {
//            val focused = interaction.command.options.entries.first {
//                it.value.focused
//            }
//            if (focused.key == "group_id") {
//                interaction.suggestInteger {
//                    bot.qq.qqBot.groups
//                        .filter {
//                            it.name.contains(focused.value.value as String)
//                        }.sortedBy { it.botAsMember.lastSpeakTimestamp }
//                        .takeLast(25).forEach {
//                            choice(it.name, it.id)
//                        }
//                }
//            }
//        }

//        resolveMissChannel(groupConfigs)

        incomingEventChannel.receiveAsFlow().collect { event ->
            runCatching {
                when (event) {
                    is GroupMessageSyncEvent,
                    is GroupMessageEvent -> {
                        handleGroupMessageEvent(event as GroupAwareMessageEvent)
                    }
                }
            }.onFailure {
                log.error("Handle qq event error", it)
                if (kord.isActive.not()) {
                    coroutineScope {
                        launch {
                            runCatching {
                                kord.login()
                            }.onFailure { ex ->
                                log.error("Try login failed", ex)
                            }
                        }
                    }
                }
            }
        }

        syncMessageChannel.receiveAsFlow().collect { source ->
            handleSyncMessage(source)
        }
    }

    private suspend fun GuildChatInputCommandInteractionCreateEvent.handleBindCommand() {
        val groupId = interaction.command.integers["group_id"]!! // it's required so it's never null
        val new = interaction.command.booleans["new_channel"] ?: false
        val group = bot.qq.qqBot.getGroup(groupId)

        if (group != null) {
            val channel = if (new) {
                val category =
                    interaction.channel.guild.channels.filterIsInstance<Category>().firstOrNull { it.name == "forward" }
                        ?: run {
                            interaction.channel.guild.createCategory("forward")
                        }
                category.createTextChannel(group.name)
            } else interaction.channel


            val config = groupConfigRepository.findByQqGroupId(groupId)
            if (config != null) {
                if (config.discordChannelId != channel.id.value.toLong()) {
                    config.discordChannelId = channel.id.value.toLong()
                    groupConfigRepository.save(config)
                }
            } else {
                groupConfigRepository.save(
                    GroupConfig(
                        groupId,
                        group.name,
                        discordChannelId = channel.id.value.toLong(),
                        id = snowFlake.nextId()
                    )
                )
            }
        }
        interaction.respondEphemeral {
            content = if (group != null) {
                "Bind group ${group.name}(${group.id})"
            } else {
                "Group $groupId not found"
            }
        }
    }

    suspend fun resolveMissChannel(groupConfigs: List<GroupConfig>) {
        val missConfigs = if (groupConfigs.isEmpty()) {
            bot.groupConfig.items.map {
                GroupConfig(it.qq, it.title, it.tg, status = it.status.joinToString(","), id = snowFlake.nextId())
            }
        } else {
            groupConfigs.filter { it.discordChannelId == null }
        }

        val guild = kord.guilds.firstOrNull() ?: return
        val category =
            guild.channels.firstOrNull { it is Category && it.name == "forward" } as? Category
                ?: guild.createCategory("forward")
        val existChannel = category.channels.toList().associateBy { it.name }

        missConfigs.forEach { config ->
            val channel = existChannel[config.name] ?: category.createTextChannel(config.name)
            config.discordChannelId = channel.id.value.toLong()
        }
        groupConfigRepository.saveAll(missConfigs)
    }

    suspend fun handleSyncMessage(source: OnlineMessageSource.Outgoing) {
        val channel = channelCache[source.target.id] ?: run {
            val channelId = groupConfigRepository.findByQqGroupId(source.target.id)?.discordChannelId ?: return
            kord.getChannel(Snowflake(channelId)) as? TextChannel ?: return
        }
        channelCache.putIfAbsent(source.target.id, channel)
        val webhook = channel.webhooks.firstOrNull() ?: channel.createWebhook("forward")
        val name = "${source.sender.nameCardOrNick} #${source.sender.id}"
        val avatarUrl = source.sender.avatarUrl
        val token = webhook.token ?: error("Webhook token is null")
        val group = source.target as? Group
        handleMessage(source.originalMessage, webhook, token, name, avatarUrl, group)
    }

    suspend fun handleGroupMessageEvent(event: GroupAwareMessageEvent) {
        val group = event.group
        val channel = channelCache[group.id] ?: run {
            val channelId = groupConfigRepository.findByQqGroupId(group.id)?.discordChannelId ?: return
            kord.getChannel(Snowflake(channelId)) as? TextChannel ?: return
        }
        channelCache.putIfAbsent(group.id, channel)
        val webhook = channel.webhooks.firstOrNull() ?: channel.createWebhook("forward")
        val name = "${bot.userConfig.idBindings[event.sender.id] ?: event.senderName} #${event.sender.id}"
        val avatarUrl = event.sender.avatarUrl
        val token = webhook.token ?: error("Webhook token is null")
        handleMessage(event.message, webhook, token, name, avatarUrl, group)
    }

    suspend fun handleMessage(
        messageChain: MessageChain,
        webhook: Webhook,
        token: String,
        senderName: String,
        avatarUrl: String,
        group: Group?
    ) {
        messageChain.forEach { message ->
            when (message) {
                is PlainText -> {
                    webhook.execute(token) {
                        this.username = senderName
                        this.avatarUrl = avatarUrl
                        this.content = message.contentToString()
                    }
                }

                is Image -> {
                    webhook.execute(token) {
                        this.username = senderName
                        this.avatarUrl = avatarUrl
                        this.content = message.queryUrl()
                    }
                }

                is FileMessage -> {
                    group?.let {
                        webhook.execute(token) {
                            this.username = senderName
                            this.avatarUrl = avatarUrl
                            this.content = message.toAbsoluteFile(group)?.getUrl()
                        }
                    }
                }

                else -> null
            }?.let { receive ->
                qqDiscordRepository.save(
                    QQDiscord(
                        messageChain.source.targetId,
                        messageChain.source.ids[0],
                        receive.channelId.value.toLong(),
                        receive.id.value.toLong()
                    )
                )
            }
        }
    }

}