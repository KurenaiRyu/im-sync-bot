package kurenai.imsyncbot.service

import it.tdlight.jni.TdApi
import jakarta.persistence.criteria.Predicate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.configuration.AbstractConfig
import kurenai.imsyncbot.domain.UserConfig
import kurenai.imsyncbot.userConfigRepository
import kurenai.imsyncbot.utils.TelegramUtil.isBot
import kurenai.imsyncbot.utils.TelegramUtil.username
import kurenai.imsyncbot.utils.json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText

/**
 * 用户配置类
 *
 * @param configPath 用户配置路径
 * @param configProperties 配置文件
 */
class UserConfigService(
    configPath: String,
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
    override val path: Path = Path.of(configPath, "user.json")

    init {
        migration()
        refresh()
    }

    override fun migration() {
        if (path.exists()) {
            val configs = json.decodeFromString(ListSerializer(User.serializer()), path.readText()).toMutableList()
            if (configs.isNotEmpty()) {
                val exists = findByQQOrTg(configs.mapNotNull { it.qq }, configs.mapNotNull { it.tg })
                val existQQ = exists.map { it.qq }.toHashSet()
                val existTG = exists.map { it.tg }.toHashSet()
                val updates = configs.filter {
                    it.qq !in existQQ && it.tg !in existTG
                }.map {
                    UserConfig().apply {
                        qq = it.qq
                        tg = it.tg
                        bindingName = it.bindingName
                        status = it.status
                    }
                }
                userConfigRepository.saveAll(updates)
                path.moveTo(path.parent.resolve("user.bak.json"))
            }
        }
    }


    fun admin(id: Long, username: String? = null, isSuper: Boolean = false) {
        val filters = configs.filter { c -> id == c.tg }
        val adminStatus = if (isSuper) UserStatus.SUPER_ADMIN else UserStatus.ADMIN
        if (filters.isEmpty()) {
            val new = UserConfig().apply {
                tg = id
                status = hashSetOf(adminStatus)
            }
            userConfigRepository.save(new)
            refresh()
        } else {
            filters.forEach { c ->
                if (!c.status.contains(adminStatus)) {
                    c.status.add(adminStatus)
                }
                if (!isSuper) c.status.remove(UserStatus.SUPER_ADMIN)
            }
            userConfigRepository.saveAll(filters)
        }
    }

    fun removeAdmin(id: Long) {
        val filters = configs.filter { c -> id == c.tg }
        filters.forEach {
            it.status.remove(UserStatus.SUPER_ADMIN)
            it.status.remove(UserStatus.ADMIN)
        }
        userConfigRepository.saveAll(filters)
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
        val filters = configs.filter { c -> tg?.let { c.tg == it } ?: qq?.let { c.qq == it } ?: false }
        if (filters.isEmpty()) {
            val new = UserConfig().apply {
                this.tg = tg
                this.qq = qq
                this.status = hashSetOf(status)
            }
            userConfigRepository.save(new)
            refresh()
        } else {
            filters.forEach { c ->
                if (!c.status.contains(UserStatus.BANNED)) {
                    c.status.add(UserStatus.BANNED)
                }
            }
            userConfigRepository.saveAll(filters)
        }
    }

    private fun removeStatus(id: Long, status: UserStatus) {
        val filters = configs.filter { c -> (id == c.tg || id == c.qq) }
        filters.forEach {
            it.status.remove(status)
        }
        userConfigRepository.saveAll(filters)
    }

    fun bindName(tg: Long? = null, qq: Long? = null, bindingName: String) {
        val filter = configs.filter { c -> tg?.let { c.tg == it } ?: qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            val new = UserConfig().apply {
                this.tg = tg
                this.qq = qq
                this.bindingName = bindingName
            }
            configs.add(new)
            userConfigRepository.save(new)
            refresh()
        } else {
            filter.forEach { c ->
                c.bindingName = bindingName
            }
            userConfigRepository.saveAll(filter)
        }
    }

    fun unbindNameByTG(id: Long) {
        val filters = configs.filter { b -> id == b.tg }
        filters.forEach {
            it.bindingName = null
        }
        afterUnbind(filters)
    }

    fun unbindNameByQQ(id: Long) {
        val filters = configs.filter { b -> id == b.qq }
        filters.forEach {
            it.bindingName = null
        }
        afterUnbind(filters)
    }

    private fun afterUnbind(filters: List<UserConfig>) {
        val deleteList = filters.filter { it.qq == null && it.tg == null }
        if (deleteList.isNotEmpty()) {
            userConfigRepository.deleteAll(deleteList)
        }
        val saveList = filters.filter { it.tg != null || it.qq != null }
        if (saveList.isNotEmpty()) {
            userConfigRepository.saveAll(saveList)
        }
    }

    fun link(tg: Long, qq: Long) {
        val list = configs.filter { it.tg == tg || it.qq == qq }
        val bindingName = list.firstNotNullOfOrNull { it.bindingName }
        val status = if (list.isEmpty()) HashSet()
        else list.map { it.status.toMutableList() }
            .reduce { acc, item -> acc.also { it.addAll(item) } }
            .distinct().toHashSet()
        val one = list.firstOrNull() ?: UserConfig().apply {
            this.tg = tg
            this.qq = qq
            this.bindingName = bindingName
            this.status = status
        }
        userConfigRepository.save(one)
        list.forEachIndexed { index, userConfig ->
            if (index > 0) {
                configs.remove(userConfig)
                userConfigRepository.delete(userConfig)
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

    override fun refresh() {
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

        configs = userConfigRepository.findAll().toMutableList()
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
                        masterUsername = config.bindingName
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

    fun add(config: UserConfig) {
        val filter = configs.filter { c -> config.tg?.let { c.tg == it } ?: config.qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            userConfigRepository.save(config)
            refresh()
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
            userConfigRepository.saveAll(filter)
        }
    }

    fun findByQQOrTg(qqList: List<Long>? = null, tgList: List<Long>? = null): List<UserConfig> {
        if (qqList?.isEmpty() == true && tgList?.isEmpty() == true) return emptyList()
        return userConfigRepository.findAll { root, query, builder ->
            val predicateList = mutableListOf<Predicate>()
            if (qqList?.isNotEmpty() == true) {
                predicateList.add(root.get<Long>("qq").`in`(qqList))
            }
            if (tgList?.isNotEmpty() == true) {
                predicateList.add(root.get<Long>("tg").`in`(tgList))
            }
            builder.or(*predicateList.toTypedArray())
        }
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