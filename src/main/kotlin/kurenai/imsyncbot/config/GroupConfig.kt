package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import it.tdlight.jni.TdApi.Message
import kurenai.imsyncbot.ImSyncBot
import java.nio.file.Path

//TODO: Migrate to DB
class GroupConfig(
    val bot: ImSyncBot,
    configPath: String,
) : AbstractConfig<Group>() {

    var defaultQQGroup: Long = 0
    var defaultTgGroup: Long = 0

    var qqTg = emptyMap<Long, Long>()
    var tgQQ = emptyMap<Long, Long>()
    var bannedGroups = emptyList<Long>()
    var picBannedGroups = emptyList<Long>()
    var filterGroups = emptyList<Long>()
    override val items = ArrayList<Group>()
    override val path = Path.of(configPath, "group.json")
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
        return items.any { it.tg == tg && it.status.contains(status) }
    }

    fun addStatus(tg: Long, status: String) {
        items.firstOrNull { it.tg == tg && !it.status.contains(status) }?.status?.add(status)
        afterUpdate()
    }

    fun removeStatus(tg: Long, status: String) {
        items.firstOrNull { it.tg == tg && it.status.contains(status) }?.status?.remove(status)
        afterUpdate()
    }

    fun add(tg: Long, qq: Long, title: String) {
        add(Group(tg, qq, title))
    }

    fun remove(tg: Long) {
        items.removeIf { it.tg == tg }
        afterUpdate()
    }

    /**
     * Set group which from message as default group
     *
     * @param message
     * @return True if set group as default group, otherwise revoke group as default group
     */
    suspend fun default(message: Message): Boolean {
        val defaultGroup = items.firstOrNull { it.status.contains(GroupStatus.DEFAULT.name) }
        val chat = bot.tg.getChat(message.chatId)
        if (defaultGroup != null) {
            defaultGroup.status.remove(GroupStatus.DEFAULT.name)
            if (message.chatId == defaultGroup.tg) return false
            else {
                add(Group(message.chatId, tgQQ[message.chatId] ?: defaultGroup.qq, chat.title, defaultGroup.status))
            }
        } else {
            add(
                Group(
                    message.chatId,
                    tgQQ[message.chatId] ?: 0L,
                    chat.title,
                    hashSetOf(GroupStatus.DEFAULT.name)
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
        for (config in items) {
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
        items.removeIf { it.tg == config.tg }
        items.add(config)
    }

    override fun addAll0(configs: Collection<Group>) {
        val tgs = configs.map { it.tg }
        items.removeIf { tgs.contains(it.tg) }
        items.addAll(configs)
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