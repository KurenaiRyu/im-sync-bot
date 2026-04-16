package kurenai.imsyncbot.bot.discord

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.commands.upsertCommand
import dev.minn.jda.ktx.jdabuilder.light
import dev.minn.jda.ktx.messages.MessageCreateBuilder
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.domain.by
import kurenai.imsyncbot.domain.copy
import kurenai.imsyncbot.repository.GroupConfigRepository
import kurenai.imsyncbot.snowFlake
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.HttpUtil
import kurenai.imsyncbot.utils.getLogger
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.utils.FileUpload
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.BotActiveEvent
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.GroupMessagePostSendEvent
import net.mamoe.mirai.event.events.GroupTempMessagePostSendEvent
import net.mamoe.mirai.message.data.FileMessage
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.OnlineMessageSource
import org.babyfish.jimmer.kt.new
import java.nio.file.Files
import java.util.*
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
        val IMAGE_SIZE: Int = 10_000_000
    }

    lateinit var jda: JDA
    val incomingEventChannel: Channel<GroupAwareMessageEvent> = Channel(Channel.BUFFERED, BufferOverflow.DROP_OLDEST)
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

    private val channelCache = WeakHashMap<Long, TextChannel>()

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

    private suspend fun initChannel() {
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

        bot.qq.qqBot.eventChannel.subscribeAlways<GroupAwareMessageEvent> {
            incomingEventChannel.trySend(it)
        }


        bot.qq.qqBot.eventChannel.subscribeAlways<BotActiveEvent> { event ->
            when (event) {
                is GroupMessagePostSendEvent -> {
                    event.receipt?.source?.let { syncMessageChannel.trySend(it) }
                }

                is GroupTempMessagePostSendEvent -> {
                    event.receipt?.source?.let { syncMessageChannel.trySend(it) }
                }
            }
        }

        launch {
            syncMessageChannel.receiveAsFlow().collect { source ->
                handleSyncMessage(source)
            }
        }
        launch {
            incomingEventChannel.receiveAsFlow().collect { event ->
                handleGroupMessageEvent(event)
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
                    event.guild!!.channels.filterIsInstance<Category>().firstOrNull { it.name == "forward" }
                        ?: run {
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
        handleMessage(source.originalMessage, webhook, name, avatarUrl, group)
    }

    suspend fun handleGroupMessageEvent(event: GroupAwareMessageEvent) {
        val group = event.group
        val channelId = GroupConfigRepository.findByQqGroupId(group.id)?.discordChannelId ?: return
        val channel = jda.getChannel<TextChannel>(channelId) ?: return
        val webhook =
            channel.retrieveWebhooks().await().firstOrNull { it.name == "forward" } ?: channel.createWebhook("forward")
                .await()
        val name = "${bot.userConfigService.idBindings[event.sender.id] ?: event.senderName} #${event.sender.id}"
        val avatarUrl = event.sender.avatarUrl
        handleMessage(event.message, webhook, name, avatarUrl, group)
    }

    suspend fun handleMessage(
        messageChain: MessageChain,
        webhook: Webhook,
        senderName: String,
        avatarUrl: String,
        group: Group
    ) {
        messageChain.forEach { message ->
            when (message) {
                is Image -> {
                    val url = message.queryUrl()
                    var path = BotUtil.downloadImg(url)
                    if (HttpUtil.getRemoteFileSize(url) >= IMAGE_SIZE) {
                        path = BotUtil.toWebp(path)
                    }
                    if (Files.size(path) > IMAGE_SIZE) {
                        error("File size is too large")
                    }

                    val msg = MessageCreateBuilder {
                        files += FileUpload.fromData(path)
                    }.build()

                    webhook.sendMessage(msg)
                        .setAvatarUrl(avatarUrl)
                        .setUsername(senderName)
                        .queue()
                }

                is FileMessage -> {

                    val url = message.toAbsoluteFile(group)?.getUrl() ?: error("Can't get document url")
                    if (HttpUtil.getRemoteFileSize(url) >= IMAGE_SIZE) error("File size is too large")

                    val raw = BotUtil.downloadImg(url)
                    val target = BotUtil.toWebp(raw)

                    val msg = MessageCreateBuilder {
                        files += FileUpload.fromData(target)
                    }.build()

                    webhook.sendMessage(msg)
                        .setAvatarUrl(avatarUrl)
                        .setUsername(senderName)
                        .queue()
                }

                else -> {
                    webhook.sendMessage(message.contentToString())
                        .setAvatarUrl(avatarUrl)
                        .setUsername(senderName)
                        .queue()
                }
            }.let { receive ->
                //                QQDiscordRepository.save(
                //                    QQDiscord().apply {
                //                        this.qqGrpId = messageChain.source.targetId
                //                        this.qqMsgId = messageChain.source.ids[0]
                //                        this.discordChannelId = receive.channelId.value.toLong()
                //                        this.discordMsgId = receive.idLong
                //                    }
                //                )
            }
        }
    }

}