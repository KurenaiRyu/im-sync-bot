package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi.Message
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.config.AbstractConfig
import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.groupConfigRepository
import kurenai.imsyncbot.snowFlake
import kurenai.imsyncbot.utils.json
import kurenai.imsyncbot.utils.withIO
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText

//TODO: Migrate to DB
class GroupConfigService(
    val bot: ImSyncBot,
    configPath: String,
) : AbstractConfig<GroupConfig>() {

    var defaultQQGroup: Long = 0
    var defaultTgGroup: Long = 0

    var qqTg = emptyMap<Long, Long>()
    var tgQQ = emptyMap<Long, Long>()
    var bannedGroups = emptyList<Long>()
    var picBannedGroups = emptyList<Long>()
    var filterGroups = emptyList<Long>()
    override lateinit var configs: MutableList<GroupConfig>
    override val path: Path = Path.of(configPath, "group.json")

    init {
        migration()
        refresh()
    }

    override fun migration() {
        if (path.exists()) {
            val configs = json.decodeFromString(ListSerializer(Group.serializer()), path.readText())
            if (configs.isEmpty().not()) {
                val exists =
                    groupConfigRepository.findAllByQqGroupIdIn(configs.map { it.qq }).associateBy { it.qqGroupId }
                val entities = configs.filter { (exists[it.qq]?.telegramGroupId ?: 0) == 0L }
                    .map {
                        exists[it.qq]?.let { exist ->
                            GroupConfig(
                                id = exist.id,
                                name = it.title,
                                qqGroupId = it.qq,
                                telegramGroupId = it.tg,
                                discordChannelId = exist.discordChannelId,
                                status = it.status,
                            )
                        } ?: run {
                            GroupConfig(
                                id = snowFlake.nextId(),
                                name = it.title,
                                qqGroupId = it.qq,
                                telegramGroupId = it.tg,
                                discordChannelId = null,
                                status = it.status,
                            )
                        }
                    }
                groupConfigRepository.saveAll(entities)
                path.moveTo(path.parent.resolve("group.bak.json"))
            }
        }
    }

    suspend fun ban(tg: Long) {
        addStatus(tg, GroupStatus.BANNED)
    }

    suspend fun unban(tg: Long) {
        removeStatus(tg, GroupStatus.BANNED)
    }

    suspend fun banPic(tg: Long) {
        addStatus(tg, GroupStatus.PIC_BANNED)
    }

    suspend fun unbanPic(tg: Long) {
        removeStatus(tg, GroupStatus.PIC_BANNED)
    }

    fun statusContain(tg: Long, status: GroupStatus): Boolean {
        return configs.any { it.telegramGroupId == tg && it.status.contains(status) }
    }

    suspend fun addStatus(tg: Long, status: GroupStatus) {
        configs.firstOrNull { it.telegramGroupId == tg && !it.status.contains(status) }?.let {
            it.status.add(status)
            withIO { groupConfigRepository.save(it) }
        }
    }

    suspend fun removeStatus(tg: Long, status: GroupStatus) {
        configs.firstOrNull { it.telegramGroupId == tg && it.status.contains(status) }?.let {
            it.status.remove(status)
            withIO { groupConfigRepository.save(it) }
        }
    }

    suspend fun bind(tg: Long, qq: Long, title: String) {
        configs.firstOrNull { it.qqGroupId == qq }?.let {
            it.telegramGroupId = tg
            it.name = title
            withIO { groupConfigRepository.save(it) }
        } ?: run {
            add(GroupConfig(qq, title, tg))
        }
    }

    suspend fun remove(tg: Long) {
        configs.firstOrNull { it.telegramGroupId == tg }?.let {
            withIO { groupConfigRepository.delete(it) }
            configs.remove(it)
        }
    }

    /**
     * Set group which from message as default group
     *
     * @param message
     * @return True if set group as default group, otherwise revoke group as default group
     */
    suspend fun defaultGroup(message: Message): Boolean {
        val defaultGroup = configs.firstOrNull { it.status.contains(GroupStatus.DEFAULT) }
        val chat = bot.tg.getChat(message.chatId)
        if (defaultGroup != null) {
            defaultGroup.status.remove(GroupStatus.DEFAULT)
            withIO { groupConfigRepository.save(defaultGroup) }
            if (message.chatId == defaultGroup.telegramGroupId) return false
            else {
                configs.firstOrNull { it.telegramGroupId == message.chatId }?.let {
                    addStatus(message.chatId, GroupStatus.DEFAULT)
                } ?: run {
                    add(
                        GroupConfig(
                            telegramGroupId = message.chatId,
                            qqGroupId = bot.groupConfigService.defaultQQGroup,
                            name = chat.title,
                            status = hashSetOf(GroupStatus.DEFAULT)
                        )
                    )
                }
            }
        } else {
            add(
                GroupConfig(
                    telegramGroupId = message.chatId,
                    qqGroupId = tgQQ[message.chatId] ?: 0L,
                    name = chat.title,
                    status = hashSetOf(GroupStatus.DEFAULT)
                )
            )
        }
        return true
    }

    override fun refresh() {
        val qqTg = HashMap<Long, Long>()
        val tgQQ = HashMap<Long, Long>()
        val bannedGroups = ArrayList<Long>()
        val picBannedGroups = ArrayList<Long>()
        val filterGroups = ArrayList<Long>()
        configs = groupConfigRepository.findAll()
            .filter { it.telegramGroupId != null }
            .toMutableList()
        for (config in configs) {
            val telegramGroupId = config.telegramGroupId!!
            qqTg[config.qqGroupId] = telegramGroupId
            tgQQ[telegramGroupId] = config.qqGroupId

            var banned = false
            for (status in config.status) {
                when (status) {
                    GroupStatus.BANNED -> {
                        bannedGroups.add(config.qqGroupId)
                        bannedGroups.add(telegramGroupId)
                        banned = true
                    }

                    GroupStatus.PIC_BANNED -> {
                        picBannedGroups.add(config.qqGroupId)
                        picBannedGroups.add(telegramGroupId)
                    }

                    GroupStatus.DEFAULT -> {
                        defaultQQGroup = config.qqGroupId
                        defaultTgGroup = telegramGroupId
                    }
                }
            }
            if (!banned) filterGroups.add(config.qqGroupId)
        }
        this.qqTg = qqTg.toMap()
        this.tgQQ = tgQQ.toMap()
        this.bannedGroups = bannedGroups.toList()
        this.picBannedGroups = picBannedGroups.toList()
        this.filterGroups = filterGroups.toList()
    }

    private suspend fun add(config: GroupConfig) {
        val exist = configs.firstOrNull { it.telegramGroupId == config.telegramGroupId }
        if (exist != null) return
        withIO { groupConfigRepository.save(config) }
        configs.add(config)
    }

    override fun getConfigName(): String {
        return "群组绑定配置"
    }

}

@Serializable
data class Group(
    val tg: Long,
    val qq: Long,
    val title: String,
    val status: HashSet<GroupStatus>,
)

enum class GroupStatus {
    BANNED,
    PIC_BANNED,
    DEFAULT,
}