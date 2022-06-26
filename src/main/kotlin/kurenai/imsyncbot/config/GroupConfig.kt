package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import moe.kurenai.tdlight.model.message.Message
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
        addStatus(tg, GroupStatus.BANNED.name)
    }

    fun unban(tg: Long) {
        removeStatus(tg, GroupStatus.BANNED.name)
    }

    fun banPic(tg: Long) {
        addStatus(tg, GroupStatus.PIC_BANNED.name)
    }

    fun unbanPic(tg: Long) {
        removeStatus(tg, GroupStatus.PIC_BANNED.name)
    }

    fun statusContain(tg: Long, status: String): Boolean {
        return configs.any { it.tg == tg && it.status.contains(status) }
    }

    fun addStatus(tg: Long, status: String) {
        configs.firstOrNull { it.tg == tg && !it.status.contains(status) }?.status?.add(status)
        afterUpdate()
    }

    fun removeStatus(tg: Long, status: String) {
        configs.removeIf { it.tg == tg && it.status.contains(status) }
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
        val defaultGroup = configs.firstOrNull { it.status.contains(GroupStatus.DEFAULT.name) }
        if (defaultGroup != null) {
            add(Group(message.chat.id, tgQQ[message.chat.id] ?: defaultGroup.qq, message.chat.title!!, defaultGroup.status))
        } else {
            add(
                Group(
                    message.chat.id,
                    tgQQ[message.chat.id] ?: 0L,
                    message.chat.title!!,
                    hashSetOf(GroupStatus.DEFAULT.name)
                )
            )
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
                    GroupStatus.BANNED.name -> {
                        bannedGroups.add(config.qq)
                        bannedGroups.add(config.tg)
                        banned = true
                    }
                    GroupStatus.PIC_BANNED.name -> {
                        picBannedGroups.add(config.qq)
                        picBannedGroups.add(config.tg)
                    }
                    GroupStatus.DEFAULT.name -> {
                        defaultQQGroup = config.qq
                        defaultTgGroup = config.tg
                    }
                }
            }
            if (!banned) filterGroups.add(config.qq)
        }
        this.qqTg = qqTg.toMap()
        this.tgQQ = tgQQ.toMap()
        this.bannedGroups = bannedGroups.toList()
        this.picBannedGroups = picBannedGroups.toList()
        this.filterGroups = filterGroups.toList()
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
    val status: HashSet<String> = HashSet(),
)

enum class GroupStatus {
    BANNED,
    PIC_BANNED,
    DEFAULT,
}