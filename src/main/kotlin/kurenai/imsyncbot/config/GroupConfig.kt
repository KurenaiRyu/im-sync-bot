package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import java.io.File

object GroupConfig : AbstractConfig<Group>() {

    val qqTg = HashMap<Long, Long>()
    val tgQQ = HashMap<Long, Long>()
    val bannedGroups = ArrayList<Long>()
    val picBannedGroups = ArrayList<Long>()
    val filterGroups = ArrayList<Long>()
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
        configs.filter { it.tg == tg }.forEach {
            if (!it.status.contains(GroupStatus.BANNED)) it.status.add(GroupStatus.BANNED)
        }
        afterUpdate()
    }

    fun unban(tg: Long) {
        configs.filter { it.tg == tg }.forEach {
            it.status.remove(GroupStatus.BANNED)
        }
        afterUpdate()
    }

    fun add(qq: Long, tg: Long, title: String) {
        add(Group(qq, tg, title))
    }

    fun remove(tg: Long) {
        configs.removeIf { it.tg == tg }
        afterUpdate()
    }

    override fun refresh() {
        qqTg.clear()
        tgQQ.clear()
        bannedGroups.clear()
        picBannedGroups.clear()
        for (config in configs) {
            qqTg[config.qq] = config.tg
            tgQQ[config.tg] = config.qq

            var banned = false
            for (status in config.status) {
                when (status) {
                    GroupStatus.BANNED -> {
                        bannedGroups.add(config.qq)
                        banned = true
                    }
                    GroupStatus.PIC_BANNED -> {
                        picBannedGroups.add(config.qq)
                    }
                }
            }
            if (!banned) filterGroups.add(config.qq)
        }
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
    val qq: Long,
    val tg: Long,
    val title: String,
    val status: HashSet<GroupStatus> = HashSet(),
)

enum class GroupStatus {
    BANNED,
    PIC_BANNED,
}