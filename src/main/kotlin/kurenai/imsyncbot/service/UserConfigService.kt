package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.configuration.AbstractConfig
import kurenai.imsyncbot.domain.UserConfig
import kurenai.imsyncbot.domain.by
import kurenai.imsyncbot.domain.copy
import kurenai.imsyncbot.repository.UserConfigRepository
import kurenai.imsyncbot.utils.getLogger
import kurenai.imsyncbot.utils.telegram.isBot
import org.babyfish.jimmer.kt.new

/**
 * 用户配置类
 *
 * @param configPath 用户配置路径
 * @param configProperties 配置文件
 */
class UserConfigService(
    configProperties: ConfigProperties
) : AbstractConfig<UserConfig>() {

    private val log = getLogger()

    var masterTg: Long = configProperties.bot.masterOfTg
    var masterQQ: Long = configProperties.bot.masterOfQq
    var masterUsername: String = ""

    var defaultChatId = configProperties.bot.privateChat

    var friendChatIds = emptyMap<Long, Long>()
    var chatIdFriends = emptyMap<Long, Long>()
    var idBindings = emptyMap<Long, String>()
    var usernameBindings = emptyMap<String, String>()
    var qqUsernames = emptyMap<Long, String>()
    var links = emptyList<UserConfig>()
    var bannedIds = emptyList<Long>()
    var picBannedIds = emptyList<Long>()
    override lateinit var configs: MutableList<UserConfig>

    init {
        runBlocking { refresh() }
    }

    fun findByTg(tg: Long?): UserConfig? {
        if (tg == null) return null
        return configs.find { it.tg == tg }
    }

    fun findByQQ(qq: Long?): UserConfig? {
        if (qq == null) return null
        return configs.find { it.qq == qq }
    }

    suspend fun admin(id: Long, username: String? = null, isSuper: Boolean = false) {
        val config = UserConfigRepository.findByTgOrQQ(id)

        val adminStatus = if (isSuper) UserStatus.SUPER_ADMIN else UserStatus.ADMIN
        if (config == null) {
            val new = new(UserConfig::class).by {
                tg = id
                status = hashSetOf(adminStatus)
            }
            UserConfigRepository.save(new)
        } else {
            UserConfigRepository.save(
                config.copy {
                    if (!this@copy.status.contains(adminStatus)) this@copy.status += adminStatus

                    if (!isSuper) this@copy.status.remove(UserStatus.SUPER_ADMIN)
                }
            )
        }
        refresh()
    }

    suspend fun removeAdmin(id: Long) {
        val config = UserConfigRepository.findByTgOrQQ(id) ?: return

        UserConfigRepository.save(config.copy {
            status.remove(UserStatus.SUPER_ADMIN)
            status.remove(UserStatus.ADMIN)
        })
        refresh()
    }

    suspend fun ban(tg: Long? = null, qq: Long? = null, username: String? = null) {
        addStatus(UserStatus.BANNED, tg, qq, username)
    }

    suspend fun unban(id: Long) {
        removeStatus(id, UserStatus.BANNED)
    }

    suspend fun banPic(tg: Long? = null, qq: Long? = null, username: String? = null) {
        addStatus(UserStatus.PIC_BANNED, tg, qq, username)
    }

    suspend fun unbanPic(id: Long) {
        removeStatus(id, UserStatus.PIC_BANNED)
    }

    private suspend fun addStatus(status: UserStatus, tg: Long? = null, qq: Long? = null, username: String? = null) {
        if (tg == null && qq == null) {
            log.warn("Tg or QQ id not found, skipped.")
            return
        }

        val config = if (tg != null) UserConfigRepository.findByTg(tg) else UserConfigRepository.findByQQ(qq!!)
        if (config == null) {
            UserConfigRepository.save(new(UserConfig::class).by {
                this.tg = tg
                this.qq = qq
                this.status = hashSetOf(status)
            })
        } else {
            if (!config.status.contains(status)) {
                UserConfigRepository.save(
                    config.copy {
                        this.status.add(status)
                    }
                )
            }
        }
        refresh()
    }

    private suspend fun removeStatus(id: Long, status: UserStatus) {
        val config = UserConfigRepository.findByTgOrQQ(id) ?: return

        if (config.status.contains(status)) {
            UserConfigRepository.save(config.copy {
                this.status.remove(status)
            })
        }
        refresh()
    }

    suspend fun bindName(tg: Long? = null, qq: Long? = null, bindingName: String) {
        val config = UserConfigRepository.findByTgOrQQ(tg ?: qq ?: return)

        if (config == null) {
            val theNew = new(UserConfig::class).by {
                this.tg = tg
                this.qq = qq
                this.bindingName = bindingName
            }
            UserConfigRepository.save(theNew)
        } else {
            UserConfigRepository.save(config.copy {
                this.bindingName = bindingName
            })
        }
        refresh()
    }

    suspend fun unbindNameByTG(id: Long) {
        doUnbind(UserConfigRepository.findByTg(id) ?: return)
    }

    suspend fun unbindNameByQQ(id: Long) {
        doUnbind(UserConfigRepository.findByQQ(id) ?: return)
    }

    private suspend fun doUnbind(config: UserConfig) {
        UserConfigRepository.save(config.copy {
            bindingName = null
        })
    }

    suspend fun link(tg: Long, qq: Long) {
        val qqConfig = UserConfigRepository.findByQQ(qq)
        val tgConfig = UserConfigRepository.findByTg(tg)

        if (qqConfig == null && tgConfig == null) {
            UserConfigRepository.save(new(UserConfig::class).by {
                this.tg = tg
                this.qq = qq
            })
        } else if (tgConfig != null) {
            UserConfigRepository.save(tgConfig.copy {
                this.qq = qq
                qqConfig?.status?.let { this.status += it }
                qqConfig?.bindingName?.let { this.bindingName = it }
            })
            if (qqConfig != null) UserConfigRepository.deleteById<UserConfig>(qqConfig.id)
        } else if (qqConfig != null) {
            UserConfigRepository.save(qqConfig.copy {
                this.tg = tg
            })
        }
        refresh()
    }

//    fun unlink(user: User) {
//        configs.remove(user)
//        configs.add(User(user.tg, username = user.username, bindingName = user.bindingName, status = user.status))
//        user.qq?.let {
//            configs.add(User(qq = user.qq, bindingName = user.bindingName, status = user.status))
//        }
//    }

    fun isQQMaster(id: Long) = masterQQ == id

    fun getPermission(user: TdApi.User?): Permission {
        if (user == null || user.isBot()) return Permission.NORMAL
        val config = findByTg(user.id) ?: return Permission.NORMAL

        return if (config.isMaster()) Permission.MASTER
        else if (config.isSuperAdmin()) Permission.SUPPER_ADMIN
        else if (config.isAdmin()) Permission.ADMIN
        else Permission.NORMAL
    }

    override suspend fun refresh() {
        val ids = HashMap<Long, String>()
        val usernames = HashMap<String, String>()
        val qqUsernames = HashMap<Long, String>()
        val links = ArrayList<UserConfig>()
        val bannedIds = ArrayList<Long>()
        val picBannedIds = ArrayList<Long>()
        val admins = ArrayList<Long>()
        val friendChats = HashMap<Long, Long>()
        val chatFriends = HashMap<Long, Long>()

        configs = UserConfigRepository.findAll().toMutableList()
        for (config in configs) {
            if (config.tg != null && config.qq != null) links.add(config)
            if (config.bindingName != null) {
                config.tg?.let { ids[it] = config.bindingName!! }
                config.qq?.let { ids[it] = config.bindingName!! }
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
                        }
                        config.qq?.let {
                            admins.add(it)
                        }
                    }

                    UserStatus.MASTER -> {
                        config.tg?.let {
                            admins.add(it)
                        }
                        config.qq?.let {
                            admins.add(it)
                        }
                        masterUsername = config.bindingName?:"Master"
                    }
                }
            }

            config.qq?.let { qq ->
                config.tg?.let { chatId ->
                    friendChats[qq] = chatId
                    chatFriends[chatId] = qq
                }
            }

        }

        admins.add(masterTg)
        admins.add(masterQQ)

        idBindings = ids.toMap()
        usernameBindings = usernames.toMap()
        this.qqUsernames = qqUsernames.toMap()
        this.links = links.toList()
        this.bannedIds = bannedIds.toList()
        this.picBannedIds = picBannedIds.toList()
        this.friendChatIds = friendChats.toMap()
        this.chatIdFriends = chatFriends.toMap()
    }

    override fun getConfigName(): String {
        return "用户名绑定配置"
    }

    suspend fun add(config: UserConfig) {
        val filter = configs.filter { c -> config.tg?.let { c.tg == it } ?: config.qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            UserConfigRepository.save(config)
            refresh()
        } else {
            filter.forEach { c ->
                UserConfigRepository.save(c.copy {
                    config.bindingName?.let { bindingName = it }
                    config.status.takeIf { it.isNotEmpty() }?.let {
                        for (s in it) {
                            if (!status.contains(s))
                                status.add(s)
                        }
                    }
                })
            }
        }
    }

    suspend fun findByQQOrTg(qqList: List<Long> = emptyList(), tgList: List<Long> = emptyList()): List<UserConfig> {
        if (qqList.isEmpty() && tgList.isEmpty()) return emptyList()
        return UserConfigRepository.findByTgOrQQ(tgList, qqList)
    }

}

enum class Permission(val level: Int) {
    ADMIN(30), SUPPER_ADMIN(20), MASTER(1), NORMAL(1000)
}

@Serializable
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


fun UserConfig.isAdmin(): Boolean {
    return status.any {
        it == UserStatus.ADMIN || it == UserStatus.SUPER_ADMIN || it == UserStatus.MASTER
    }
}

fun UserConfig.isSuperAdmin(): Boolean {
    return status.any {
        it == UserStatus.SUPER_ADMIN || it == UserStatus.MASTER
    }
}

fun UserConfig.isMaster(): Boolean {
    return status.any {
        it == UserStatus.MASTER
    }
}