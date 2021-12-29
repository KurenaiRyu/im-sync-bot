package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import kurenai.imsyncbot.ContextHolder
import okhttp3.internal.toImmutableList
import okhttp3.internal.toImmutableMap
import java.io.File

object UserConfig : AbstractConfig<User>() {

    var idBindings = emptyMap<Long, String>()
    var usernameBindings = emptyMap<String, String>()
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

    override fun refresh() {
        val ids = HashMap<Long, String>()
        val usernames = HashMap<String, String>()
        val bannedIds = ArrayList<Long>()
        val picBannedIds = ArrayList<Long>()
        val admins = ArrayList<Long>()
        val superAdmins = ArrayList<Long>()

        superAdmins.addAll(ContextHolder.masterOfTg)
        admins.addAll(ContextHolder.masterOfTg)

        for (config in configs) {
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
                }
            }
        }
        idBindings = ids.toImmutableMap()
        usernameBindings = usernames.toImmutableMap()
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
    var status: HashSet<UserStatus> = HashSet(),
)

enum class UserStatus {
    ADMIN,
    SUPER_ADMIN,
    BANNED,
    PIC_BANNED,
}