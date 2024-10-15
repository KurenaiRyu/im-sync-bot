package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import jakarta.persistence.criteria.Predicate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.configuration.AbstractConfig
import kurenai.imsyncbot.domain.UserConfig
import kurenai.imsyncbot.domain.by
import kurenai.imsyncbot.domain.copy
import kurenai.imsyncbot.repository.UserConfigRepository
import kurenai.imsyncbot.utils.isBot
import kurenai.imsyncbot.utils.username
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
    var admins = emptyList<Long>()
    var superAdmins = emptyList<Long>()
    override lateinit var configs: MutableList<UserConfig>

    init {
        runBlocking { refresh() }
    }

    suspend fun admin(id: Long, username: String? = null, isSuper: Boolean = false) {
        val filters = configs.filter { c -> id == c.tg }
        val adminStatus = if (isSuper) UserStatus.SUPER_ADMIN else UserStatus.ADMIN
        if (filters.isEmpty()) {
            val new = new(UserConfig::class).by {
                tg = id
                status = listOf(adminStatus)
            }
            UserConfigRepository.save(new)
            refresh()
        } else {
            filters.map { c ->
                c.copy {
                    if (status?.contains(adminStatus) == false) {
                        status().add(adminStatus)
                    }
                    if (!isSuper) status().remove(UserStatus.SUPER_ADMIN)
                }
            }.let {
                UserConfigRepository.saveAll(it)
            }
        }
    }

    suspend fun removeAdmin(id: Long) {
        val filters = configs.filter { c -> id == c.tg }
        filters.map {
            it.copy {
                status().remove(UserStatus.SUPER_ADMIN)
                status().remove(UserStatus.ADMIN)
            }
        }.let {
            UserConfigRepository.saveAll(it)
        }
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
        val filters = configs.filter { c -> tg?.let { c.tg == it } ?: qq?.let { c.qq == it } ?: false }
        if (filters.isEmpty()) {
            UserConfigRepository.save(new(UserConfig::class).by {
                this.tg = tg
                this.qq = qq
                this.status = listOf(status)
            })
            refresh()
        } else {
            filters.map { c ->
                if (c.status?.contains(UserStatus.BANNED) == false) {
                    UserConfigRepository.save(
                        c.copy {
                            this.status().add(UserStatus.BANNED)
                        }
                    )
                }
            }
        }
    }

    private suspend fun removeStatus(id: Long, status: UserStatus) {
        val filters = configs.filter { c -> (id == c.tg || id == c.qq) }
        filters.forEach {
            UserConfigRepository.save(it.copy {
                status().remove(status)
            })
        }
    }

    suspend fun bindName(tg: Long? = null, qq: Long? = null, bindingName: String) {
        val filter = configs.filter { c -> tg?.let { c.tg == it } ?: qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            val theNew = new(UserConfig::class).by {
                this.tg = tg
                this.qq = qq
                this.bindingName = bindingName
            }
            configs.add(theNew)
            UserConfigRepository.save(theNew)
            refresh()
        } else {
            filter.forEach { c ->
                UserConfigRepository.save(c.copy {
                    this.bindingName = bindingName
                })
            }
        }
    }

    suspend fun unbindNameByTG(id: Long) {
        doUnbind(configs.filter { b -> id == b.tg })
    }

    suspend fun unbindNameByQQ(id: Long) {
        doUnbind(configs.filter { b -> id == b.qq })
    }

    private suspend fun doUnbind(filters: List<UserConfig>) {
        val deleteList = filters.filter { it.qq == null && it.tg == null }
        if (deleteList.isNotEmpty()) {
            UserConfigRepository.deleteByIds<UserConfig>(deleteList.map(UserConfig::id))
        }
        val saveList = filters.filter { it.tg != null || it.qq != null }
        if (saveList.isNotEmpty()) {
            saveList.forEach {
                UserConfigRepository.save(it.copy {
                    bindingName = null
                })
            }
        }
    }

    suspend fun link(tg: Long, qq: Long) {
        val list = configs.filter { it.tg == tg || it.qq == qq }
        val bindingName = list.firstNotNullOfOrNull { it.bindingName }
        val status = if (list.isEmpty()) HashSet()
        else list.map { it.status.toMutableList() }
            .reduce { acc, item -> acc.also { it.addAll(item) } }
            .distinct().toHashSet()
        val one = list.firstOrNull() ?: new(UserConfig::class).by {
            this.tg = tg
            this.qq = qq
            this.bindingName = bindingName
            this.status().addAll(status)
        }
        UserConfigRepository.save(one)
        list.forEachIndexed { index, userConfig ->
            if (index > 0) {
                configs.remove(userConfig)
                UserConfigRepository.deleteById<UserConfig>(userConfig.id)
            }
        }
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
        return if (user == null || user.isBot()) Permission.NORMAL
        else if (masterTg == user.id
            || masterUsername == user.username()
        ) Permission.MASTER
        else if (user.id.let(superAdmins::contains)) Permission.SUPPER_ADMIN
        else if (user.id.let(admins::contains)) Permission.ADMIN
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
        val superAdmins = ArrayList<Long>()
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
                        }
                        config.qq?.let {
                            admins.add(it)
                            superAdmins.add(it)
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
        superAdmins.add(masterTg)
        admins.add(masterQQ)
        superAdmins.add(masterQQ)

        idBindings = ids.toMap()
        usernameBindings = usernames.toMap()
        this.qqUsernames = qqUsernames.toMap()
        this.links = links.toList()
        this.bannedIds = bannedIds.toList()
        this.picBannedIds = picBannedIds.toList()
        this.admins = admins.toList()
        this.superAdmins = superAdmins.toList()
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
                            if (status?.contains(s) == false)
                                status().add(s)
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