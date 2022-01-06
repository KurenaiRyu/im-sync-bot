package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import okhttp3.internal.toImmutableList
import okhttp3.internal.toImmutableMap
import org.telegram.telegrambots.meta.api.objects.Message
import java.io.File

object GroupConfig : AbstractConfig<Group>() {

    var defaultQQGroup: Long = 0
    var defaultTgGroup: Long = 0

    var qqTg = emptyMap<Long, Long>()
    var tgQQ = emptyMap<Long, Long>()
    var bannedGroups = emptyList<Long>()
    var picBannedGroups = emptyList<Long>()
    var filterGroups = emptyList<Long>()
    override val configs = ArrayList<Group>()
    override val file = File("./config/group.json")
    override val typeRef = object : TypeReference<ArrayList<Group>>() {}

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            save()
        })
        load()
    }

    fun ban(tg: Long) {
        addStatus(tg, GroupStatus.BANNED)
    }

    fun unban(tg: Long) {
        removeStatus(tg, GroupStatus.BANNED)
    }

    fun banPic(tg: Long) {
        addStatus(tg, GroupStatus.PIC_BANNED)
    }

    fun unbanPic(tg: Long) {
        removeStatus(tg, GroupStatus.PIC_BANNED)
    }

    private fun addStatus(tg: Long, status: GroupStatus) {
        configs.filter { it.tg == tg && !it.status.contains(status) }.forEach {
            it.status.add(status)
        }
        afterUpdate()
    }

    private fun removeStatus(tg: Long, status: GroupStatus) {
        configs.filter { it.tg == tg && it.status.contains(status) }.forEach {
            it.status.remove(status)
        }
        afterUpdate()
    }

    fun add(tg: Long, qq: Long, title: String) {
        add(Group(tg, qq, title))
    }

    fun remove(tg: Long) {
        configs.removeIf { it.tg == tg }
        afterUpdate()
    }

    fun default(message: Message) {
        val defaultGroup = configs.firstOrNull { it.status.contains(GroupStatus.DEFAULT) }
        if (defaultGroup != null) {
            add(Group(message.chatId, tgQQ[message.chatId] ?: defaultGroup.qq, message.chat.title, defaultGroup.status))
        } else {
            add(Group(message.chatId, tgQQ[message.chatId] ?: 0L, message.chat.title, hashSetOf(GroupStatus.DEFAULT)))
        }
    }

    override fun refresh() {
        val qqTg = HashMap<Long, Long>()
        val tgQQ = HashMap<Long, Long>()
        val bannedGroups = ArrayList<Long>()
        val picBannedGroups = ArrayList<Long>()
        val filterGroups = ArrayList<Long>()
        for (config in configs) {
            qqTg[config.qq] = config.tg
            tgQQ[config.tg] = config.qq

            var banned = false
            for (status in config.status) {
                when (status) {
                    GroupStatus.BANNED -> {
                        bannedGroups.add(config.qq)
                        bannedGroups.add(config.tg)
                        banned = true
                    }
                    GroupStatus.PIC_BANNED -> {
                        picBannedGroups.add(config.qq)
                        picBannedGroups.add(config.tg)
                    }
                    GroupStatus.DEFAULT -> {
                        defaultQQGroup = config.qq
                        defaultTgGroup = config.tg
                    }
                }
            }
            if (!banned) filterGroups.add(config.qq)
        }
        this.qqTg = qqTg.toImmutableMap()
        this.tgQQ = tgQQ.toImmutableMap()
        this.bannedGroups = bannedGroups.toImmutableList()
        this.picBannedGroups = picBannedGroups.toImmutableList()
        this.filterGroups = filterGroups.toImmutableList()
    }

    override fun add0(config: Group) {
        configs.removeIf { it.tg == config.tg }
        configs.add(config)
    }

    override fun addAll0(configs: Collection<Group>) {
        val tgs = configs.map { it.tg }
        GroupConfig.configs.removeIf { tgs.contains(it.tg) }
        GroupConfig.configs.addAll(configs)
    }

    override fun getConfigName(): String {
        return "群组绑定配置"
    }

}

data class Group(
    val tg: Long,
    val qq: Long,
    val title: String,
    val status: HashSet<GroupStatus> = HashSet(),
)

enum class GroupStatus {
    BANNED,
    PIC_BANNED,
    DEFAULT,
}