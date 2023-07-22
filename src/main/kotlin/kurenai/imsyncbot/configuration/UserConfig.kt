package kurenai.imsyncbot.configuration

import it.tdlight.jni.TdApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.utils.TelegramUtil.isBot
import kurenai.imsyncbot.utils.TelegramUtil.username
import kurenai.imsyncbot.utils.json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * 用户配置类
 *
 * @param configPath 用户配置路径
 * @param configProperties 配置文件
 */
class UserConfig(
    configPath: String,
    configProperties: ConfigProperties
) : AbstractConfig<User>() {

    var masterTg: Long = configProperties.bot.masterOfTg
    var masterQQ: Long = configProperties.bot.masterOfQq
    var masterUsername: String = ""

    var defaultChatId = configProperties.bot.privateChat

    var friendChatIds = emptyMap<Long, Long>()
    var chatIdFriends = emptyMap<Long, Long>()
    var idBindings = emptyMap<Long, String>()
    var usernameBindings = emptyMap<String, String>()
    var qqUsernames = emptyMap<Long, String>()
    var links = emptyList<User>()
    var bannedIds = emptyList<Long>()
    var picBannedIds = emptyList<Long>()
    var admins = emptyList<Long>()
    var superAdmins = emptyList<Long>()
    override lateinit var configs: MutableList<User>
    override val path: Path = Path.of(configPath, "user.json")

    init {
        load()
    }

    fun load() {

        if (path.exists()) {
            configs = json.decodeFromString(ListSerializer(User.serializer()), path.readText()).toMutableList()
            if (configs.isNotEmpty()) {
                this.configs.clear()
                this.configs.addAll(configs)
                refresh()
            }
        }
    }

    override fun migration() {
        TODO("Not yet implemented")
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
    }

    fun removeAdmin(id: Long) {
        configs.filter { c -> id == c.tg }.forEach {
            it.status.remove(UserStatus.SUPER_ADMIN)
            it.status.remove(UserStatus.ADMIN)
        }
    }

    fun unbindChat(chatId: Long) {
        if (chatId == masterTg) return
        configs.filter {
            it.chatId == chatId
        }.forEach {
            it.chatId = null
        }
    }

    fun bindChat(qq: Long, chatId: Long) {
        if (qq == masterQQ) return
        val list = configs.filter { it.qq == qq }
        if (list.isEmpty()) {
            add(User(qq = qq, chatId = chatId))
        } else {
            list.forEach { it.chatId = chatId }
        }
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
    }

    private fun removeStatus(id: Long, status: UserStatus) {
        configs.filter { c -> (id == c.tg || id == c.qq) }.forEach {
            it.status.remove(status)
        }
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
    }

    fun unlink(user: User) {
        configs.remove(user)
        configs.add(User(user.tg, username = user.username, bindingName = user.bindingName, status = user.status))
        user.qq?.let {
            configs.add(User(qq = user.qq, bindingName = user.bindingName, status = user.status))
        }
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
    }

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

    fun setMaster(message: TdApi.Message) {
        val master = configs.firstOrNull { it.status.contains(UserStatus.ADMIN) }
        if (master == null) {
            configs.add(
                User(
                    masterTg,
                    masterQQ,
                    chatId = message.chatId,
                    status = hashSetOf(UserStatus.MASTER)
                )
            )
        } else {
            master.chatId = message.chatId
        }
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
        val friendChats = HashMap<Long, Long>()
        val chatFriends = HashMap<Long, Long>()

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
                        config.username?.let { masterUsername = it }
                    }
                }
            }

            config.qq?.let { qq ->
                config.chatId?.let { chatId ->
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

    fun add(config: User) {
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