package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import moe.kurenai.tdlight.model.message.Message
import okhttp3.internal.toImmutableList
import okhttp3.internal.toImmutableMap
import java.io.File

object UserConfig : AbstractConfig<User>() {

    var masterTg: Long = 0
    var masterQQ: Long = 0
    var masterChatId: Long = 0
    var masterUsername: String = ""

    var idBindings = emptyMap<Long, String>()
    var usernameBindings = emptyMap<String, String>()
    var qqUsernames = emptyMap<Long, String>()
    var links = emptyList<User>()
    var bannedIds = emptyList<Long>()
    var picBannedIds = emptyList<Long>()
    var admins = emptyList<Long>()
    var superAdmins = emptyList<Long>()
    override val configs = ArrayList<User>()
    override val file = File("./config/user.json")
    override val typeRef = object : TypeReference<ArrayList<User>>() {}

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            save()
        })
        load()
    }

    fun admin(id: Long, username: String? = null, isSuper: Boolean = false) {
        val filter = configs.filter { c -> id == c.tg }
        val adminStatus = if (isSuper) UserStatus.SUPER_ADMIN else UserStatus.ADMIN
        if (filter.isEmpty()) {
            configs.add(User(id, username = username, status = hashSetOf(adminStatus)))
        } else {
            filter.forEach { c ->
                username?.let { c.username = username }
                if (!c.status.contains(adminStatus)) {
                    c.status.add(adminStatus)
                }
                if (!isSuper) c.status.remove(UserStatus.SUPER_ADMIN)
            }
        }
        afterUpdate()
    }

    fun removeAdmin(id: Long) {
        configs.filter { c -> id == c.tg }.forEach {
            it.status.remove(UserStatus.SUPER_ADMIN)
            it.status.remove(UserStatus.ADMIN)
        }
        afterUpdate()
    }

    fun ban(tg: Long? = null, qq: Long? = null, username: String? = null) {
        addStatus(UserStatus.BANNED, tg, qq, username)
    }

    fun unban(id: Long) {
        removeStatus(id, UserStatus.BANNED)
    }

    fun banPic(tg: Long? = null, qq: Long? = null, username: String? = null) {
        addStatus(UserStatus.PIC_BANNED, tg, qq, username)
    }

    fun unbanPic(id: Long) {
        removeStatus(id, UserStatus.PIC_BANNED)
    }

    private fun addStatus(status: UserStatus, tg: Long? = null, qq: Long? = null, username: String? = null) {
        val filter = configs.filter { c -> tg?.let { c.tg == it } ?: qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            configs.add(User(tg, qq, username, status = hashSetOf(status)))
        } else {
            filter.forEach { c ->
                username?.let { c.username = username }
                if (!c.status.contains(UserStatus.BANNED)) {
                    c.status.add(UserStatus.BANNED)
                }
            }
        }
        afterUpdate()
    }

    private fun removeStatus(id: Long, status: UserStatus) {
        configs.filter { c -> (id == c.tg || id == c.qq) }.forEach {
            it.status.remove(status)
        }
        afterUpdate()
    }

    fun bindName(tg: Long? = null, qq: Long? = null, bindingName: String, username: String? = null) {
        val filter = configs.filter { c -> tg?.let { c.tg == it } ?: qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            configs.add(User(tg, qq, username, bindingName))
        } else {
            filter.forEach { c ->
                username?.let { c.username = username }
                c.bindingName = bindingName
            }
        }
        afterUpdate()
    }

    fun link(tg: Long, qq: Long, username: String) {
        val list = configs.filter { it.tg == tg || it.qq == qq }
        configs.removeIf { it.tg == tg || it.qq == qq }
        val bindingName = list.mapNotNull { it.bindingName }.firstOrNull()
        val status = if (list.isEmpty()) HashSet()
        else list.map { it.status.toMutableList() }
            .reduce { acc, item -> acc.also { it.addAll(item) } }
            .distinct().toHashSet()
        configs.add(User(tg, qq, username, bindingName, status = status))
        afterUpdate()
    }

    fun unlink(user: User) {
        configs.remove(user)
        configs.add(User(user.tg, username = user.username, bindingName = user.bindingName, status = user.status))
        user.qq?.let {
            configs.add(User(qq = user.qq, bindingName = user.bindingName, status = user.status))
        }
        afterUpdate()
    }

    fun unbindUsername(id: Long? = null, username: String? = null) {
        configs.removeIf { b ->
            if (id != null && id == b.tg || id == b.qq) {
                if (b.status.isEmpty()) {
                    true
                } else {
                    b.bindingName = null
                    false
                }
            } else if (username != null && username == b.username) {
                if (b.status.isEmpty()) {
                    true
                } else {
                    b.bindingName = null
                    false
                }
            } else false
        }
        afterUpdate()
    }

    fun setMaster(message: Message) {
        setMaster(User(message.from?.id, masterQQ, message.from?.username, chatId = message.chat.id))
    }

    fun setMaster(properties: ForwardHandlerProperties) {
        setMaster(User(properties.masterOfTg[0], properties.masterOfQq[0]))
    }

    fun setMaster(user: User) {
        if (!user.status.contains(UserStatus.MASTER)) user.status.add(UserStatus.MASTER)
        val master = configs.firstOrNull { it.status.contains(UserStatus.MASTER) }
        if (master == null) {
            if (!user.status.contains(UserStatus.MASTER)) user.status.add(UserStatus.MASTER)
            configs.add(user)
        } else {
            configs.remove(master)

            for (s in master.status) {
                if (!user.status.contains(s)) user.status.add(s)
            }

            configs.add(
                User(
                    user.tg ?: master.tg,
                    user.qq ?: master.qq,
                    user.username ?: master.username,
                    user.bindingName ?: master.bindingName,
                    user.chatId ?: master.chatId,
                    user.status
                )
            )
        }
        afterUpdate()
    }

    override fun refresh() {
        val ids = HashMap<Long, String>()
        val usernames = HashMap<String, String>()
        val qqUsernames = HashMap<Long, String>()
        val links = ArrayList<User>()
        val bannedIds = ArrayList<Long>()
        val picBannedIds = ArrayList<Long>()
        val admins = ArrayList<Long>()
        val superAdmins = ArrayList<Long>()

        for (config in configs) {
            if (config.tg != null && config.qq != null) links.add(config)
            if (config.username?.isNotBlank() == true && config.qq != null) {
                qqUsernames[config.qq] = config.username!!
            }
            if (config.bindingName != null) {
                config.tg?.let { ids[it] = config.bindingName!! }
                config.qq?.let { ids[it] = config.bindingName!! }
                config.username?.let { usernames[it] = config.bindingName!! }
            }
            for (status in config.status) {
                when (status) {
                    UserStatus.BANNED -> {
                        config.tg?.let(bannedIds::add)
                        config.qq?.let(bannedIds::add)
                    }
                    UserStatus.PIC_BANNED -> {
                        config.tg?.let(picBannedIds::add)
                        config.qq?.let(picBannedIds::add)
                    }
                    UserStatus.ADMIN -> {
                        config.tg?.let(admins::add)
                        config.qq?.let(admins::add)
                    }
                    UserStatus.SUPER_ADMIN -> {
                        config.tg?.let {
                            admins.add(it)
                            superAdmins.add(it)
                        }
                        config.qq?.let {
                            admins.add(it)
                            superAdmins.add(it)
                        }
                    }
                    UserStatus.MASTER -> {
                        config.tg?.let {
                            admins.add(it)
                            superAdmins.add(it)
                            masterTg = it
                        }
                        config.qq?.let {
                            admins.add(it)
                            superAdmins.add(it)
                            masterQQ = it
                        }
                        config.username?.let { masterUsername = it }
                        config.chatId?.let { masterChatId = it }
                    }
                }
            }
        }

        idBindings = ids.toImmutableMap()
        usernameBindings = usernames.toImmutableMap()
        this.qqUsernames = qqUsernames.toImmutableMap()
        this.links = links.toImmutableList()
        this.bannedIds = bannedIds.toImmutableList()
        this.picBannedIds = picBannedIds.toImmutableList()
        this.admins = admins.toImmutableList()
        this.superAdmins = superAdmins.toImmutableList()
    }

    override fun getConfigName(): String {
        return "用户名绑定配置"
    }

    override fun add0(config: User) {
        val filter = configs.filter { c -> config.tg?.let { c.tg == it } ?: config.qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            configs.add(config)
        } else {
            filter.forEach { c ->
                config.bindingName?.let { c.bindingName = it }
                config.status.takeIf { it.isNotEmpty() }?.let {
                    for (status in it) {
                        if (!c.status.contains(status)) {
                            c.status.add(status)
                        }
                    }
                }
            }
        }
    }

    override fun addAll0(configs: Collection<User>) {
        val ids = configs.mapNotNull { it.tg }
        val usernameList = configs.mapNotNull { it.username }
        this.configs.removeIf { ids.contains(it.tg) || usernameList.contains(it.username) }
        this.configs.addAll(configs)
    }

}

data class User(
    val tg: Long? = null,
    val qq: Long? = null,
    var username: String? = null,
    var bindingName: String? = null,
    var chatId: Long? = null,
    var status: HashSet<UserStatus> = HashSet(),
)

enum class UserStatus {
    ADMIN,
    SUPER_ADMIN,
    MASTER,
    BANNED,
    PIC_BANNED
}