package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi.Message
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.configuration.AbstractConfig
import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.domain.by
import kurenai.imsyncbot.domain.copy
import kurenai.imsyncbot.repository.GroupConfigRepository
import kurenai.imsyncbot.utils.withIO
import org.babyfish.jimmer.kt.new

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

    init {
        runBlocking { refresh() }
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
            save(it.copy {
                this.status += status
            })
        }
    }

    suspend fun removeStatus(tg: Long, status: GroupStatus) {
        configs.firstOrNull { it.telegramGroupId == tg && it.status.contains(status) }?.also {
            save(it.copy {
                this.status -= status
            })
        }
    }

    suspend fun bind(tg: Long, qq: Long, title: String) {
        configs.firstOrNull { it.telegramGroupId == tg }?.also {
            //Only modify qq when exist
            save(it.copy {
                name = title
                qqGroupId = qq
            })
        } ?: run {
            save(new(GroupConfig::class).by {
                this.qqGroupId = qq
                this.name = title
                this.telegramGroupId = tg
            })
        }
    }

    /**
     * Remove group bind by telegram id
     *
     * @param tg telegram id
     */
    suspend fun remove(tg: Long) {
        configs.firstOrNull { it.telegramGroupId == tg }?.also {
            delete(it)
        }
    }

    /**
     * Set group which from message as default group or not
     *
     * @param message
     * @return True if set group as default group, otherwise revoke group as default group
     */
    suspend fun defaultGroup(message: Message): Boolean {
        val defaultGroup = configs.firstOrNull { it.status.contains(GroupStatus.DEFAULT) }
        val chat = bot.tg.getChat(message.chatId)
        if (defaultGroup != null) {
            val updateList = mutableListOf(defaultGroup.copy {
                status -= GroupStatus.DEFAULT
            })

            val theNew = if (message.chatId == defaultGroup.telegramGroupId) return false
            else {
                configs.firstOrNull { it.telegramGroupId == message.chatId }?.let {
                    it.copy {
                        this.status += GroupStatus.DEFAULT
                    }
                } ?: run {
                    new(GroupConfig::class).by {
                        telegramGroupId = message.chatId
                        qqGroupId = tgQQ[message.chatId] ?: 0L
                        name = chat.title
                        status = hashSetOf(GroupStatus.DEFAULT)
                    }
                }
            }
            updateList.add(theNew)
            saveAll(updateList)
        } else {
            save(
                new(GroupConfig::class).by {
                    telegramGroupId = message.chatId
                    qqGroupId = tgQQ[message.chatId] ?: 0L
                    name = chat.title
                    status = hashSetOf(GroupStatus.DEFAULT)
                }
            )
        }
        return true
    }

    override suspend fun refresh() {
        val qqTg = HashMap<Long, Long>()
        val tgQQ = HashMap<Long, Long>()
        val bannedGroups = ArrayList<Long>()
        val picBannedGroups = ArrayList<Long>()
        val filterGroups = ArrayList<Long>()
        configs = GroupConfigRepository.findAll()
            .toMutableList()
        for (config in configs) {
            val telegramGroupId = config.telegramGroupId
            config.qqGroupId?.let {
                qqTg[it] = telegramGroupId
                tgQQ[telegramGroupId] = tgQQ[it] ?: 0L
            }

            var banned = false
            for (status in config.status) {
                when (status) {
                    GroupStatus.BANNED -> {
                        config.qqGroupId?.let { bannedGroups.add(it) }
                        bannedGroups.add(telegramGroupId)
                        banned = true
                    }

                    GroupStatus.PIC_BANNED -> {
                        config.qqGroupId?.let { picBannedGroups.add(it) }
                        picBannedGroups.add(telegramGroupId)
                    }

                    GroupStatus.DEFAULT -> {
                        defaultQQGroup = config.qqGroupId?:0
                        defaultTgGroup = telegramGroupId
                    }
                }
            }
            if (!banned) config.qqGroupId?.let { filterGroups.add(it) }
        }
        this.qqTg = qqTg.toMap()
        this.tgQQ = tgQQ.toMap()
        this.bannedGroups = bannedGroups.toList()
        this.picBannedGroups = picBannedGroups.toList()
        this.filterGroups = filterGroups.toList()
    }

    /**
     * Save [GroupConfig]
     *
     * 需要调用[GroupConfigRepository.save]需要统一调用该方法，为了能够统一处理修改后的一些操作
     *
     * @param configs Group config list to save
     */
    private suspend fun saveAll(configs: List<GroupConfig>) {
        GroupConfigRepository.saveAll(configs)
        refresh()
    }

    /**
     * Save [GroupConfig]
     *
     * 需要调用[GroupConfigRepository.save]需要统一调用该方法，为了能够统一处理修改后的一些操作
     *
     * @param config Group config to save
     */
    private suspend fun save(config: GroupConfig) {
        GroupConfigRepository.save(config)
        refresh()
    }

    /**
     * Delete [GroupConfig]
     *
     * 需要调用[GroupConfigRepository.delete]需要统一调用该方法，为了能够统一处理修改后的一些操作
     *
     * @param config Group config
     */
    private suspend fun delete(config: GroupConfig) {
        withIO { GroupConfigRepository.deleteById<GroupConfig>(config.id) }
        refresh()
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