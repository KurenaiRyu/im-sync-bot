package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import java.io.File

object UserConfig : AbstractConfig<User>() {

    val idBindings = HashMap<Long, String>()
    val usernameBindings = HashMap<String, String>()
    val bannedIds = ArrayList<Long>()
    val picBannedIds = ArrayList<Long>()
    val admins = ArrayList<Long>()
    val superAdmins = ArrayList<Long>()
    override val configs = ArrayList<User>()
    override val file = File("./config/user.json")
    override val typeRef = object : TypeReference<ArrayList<User>>() {}

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            save()
        })
        load()
    }

    fun add(tg: Long? = null, qq: Long?, bindingName: String, username: String? = null) {
        add(User(tg, bindingName = bindingName, username = username))
    }

    fun remove(id: Long? = null, username: String? = null) {
        configs.removeIf { b -> id?.let { b.tg == it } ?: username?.let { b.username == it } ?: false }
    }

    override fun refresh() {
        idBindings.clear()
        usernameBindings.clear()
        bannedIds.clear()
        picBannedIds.clear()
        admins.clear()
        superAdmins.clear()
        for (config in configs) {
            if (config.bindingName != null) {
                config.tg?.let { idBindings[it] = config.bindingName!! }
                config.qq?.let { idBindings[it] = config.bindingName!! }
                config.username?.let { usernameBindings[it] = config.bindingName!! }
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
    }

    override fun getConfigName(): String {
        return "用户名绑定配置"
    }

    override fun add0(config: User) {
        configs.removeIf { b -> b.tg?.takeIf { it == config.tg } != null || b.username?.takeIf { it == config.username } != null }
        configs.add(config)
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
    val username: String? = null,
    var bindingName: String? = null,
    var status: HashSet<UserStatus> = HashSet(),
)

enum class UserStatus {
    ADMIN,
    SUPER_ADMIN,
    BANNED,
    PIC_BANNED,
}