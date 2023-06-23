package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import kurenai.imsyncbot.ConfigProperties
import java.nio.file.Path

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
    var masterChatId: Long = 0
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
    override val items = ArrayList<User>()
    override val path = Path.of(configPath, "user.json")
    override val typeRef = object : TypeReference<ArrayList<User>>() {}

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            save()
        })
        load()
    }

    fun admin(id: Long, username: String? = null, isSuper: Boolean = false) {
        val filter = items.filter { c -> id == c.tg }
        val adminStatus = if (isSuper) UserStatus.SUPER_ADMIN else UserStatus.ADMIN
        if (filter.isEmpty()) {
            items.add(User(id, username = username, status = hashSetOf(adminStatus)))
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
        items.filter { c -> id == c.tg }.forEach {
            it.status.remove(UserStatus.SUPER_ADMIN)
            it.status.remove(UserStatus.ADMIN)
        }
        afterUpdate()
    }

    fun unbindChat(chatId: Long) {
        if (chatId == masterTg) return
        items.filter {
            it.chatId == chatId
        }.forEach {
            it.chatId = null
        }
        afterUpdate()
    }

    fun bindChat(qq: Long, chatId: Long) {
        if (qq == masterQQ) return
        val list = items.filter { it.qq == qq }
        if (list.isEmpty()) {
            add0(User(qq = qq, chatId = chatId))
        } else {
            list.forEach { it.chatId = chatId }
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
        val filter = items.filter { c -> tg?.let { c.tg == it } ?: qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            items.add(User(tg, qq, username, status = hashSetOf(status)))
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
        items.filter { c -> (id == c.tg || id == c.qq) }.forEach {
            it.status.remove(status)
        }
        afterUpdate()
    }

    fun bindName(tg: Long? = null, qq: Long? = null, bindingName: String, username: String? = null) {
        val filter = items.filter { c -> tg?.let { c.tg == it } ?: qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            items.add(User(tg, qq, username, bindingName))
        } else {
            filter.forEach { c ->
                username?.let { c.username = username }
                c.bindingName = bindingName
            }
        }
        afterUpdate()
    }

    fun link(tg: Long, qq: Long, username: String) {
        val list = items.filter { it.tg == tg || it.qq == qq }
        items.removeIf { it.tg == tg || it.qq == qq }
        val bindingName = list.mapNotNull { it.bindingName }.firstOrNull()
        val status = if (list.isEmpty()) HashSet()
        else list.map { it.status.toMutableList() }
            .reduce { acc, item -> acc.also { it.addAll(item) } }
            .distinct().toHashSet()
        items.add(User(tg, qq, username, bindingName, status = status))
        afterUpdate()
    }

    fun unlink(user: User) {
        items.remove(user)
        items.add(User(user.tg, username = user.username, bindingName = user.bindingName, status = user.status))
        user.qq?.let {
            items.add(User(qq = user.qq, bindingName = user.bindingName, status = user.status))
        }
        afterUpdate()
    }

    fun unbindUsername(id: Long? = null, username: String? = null) {
        items.removeIf { b ->
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

//    fun setMaster(message: Message) {
//        val master = items.firstOrNull { it.status.contains(UserStatus.ADMIN) }
//        if (master == null) {
//            items.add(User(masterTg, masterQQ, message.from?.firstName, chatId = message.chat.id, status = hashSetOf(UserStatus.MASTER)))
//        } else {
//
//            message.userSender()?.userId?.firstName?.let {
//                if (master.username == null) {
//                    master.username = it
//                    masterUsername = it
//                }
//            }
//            masterChatId = message.chat.id
//            master.chatId = message.chat.id
//        }
//    }

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

        for (config in items) {
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
                        config.chatId?.let { masterChatId = it }
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

    override fun add0(config: User) {
        val filter = items.filter { c -> config.tg?.let { c.tg == it } ?: config.qq?.let { c.qq == it } ?: false }
        if (filter.isEmpty()) {
            items.add(config)
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
        this.items.removeIf { ids.contains(it.tg) || usernameList.contains(it.username) }
        this.items.addAll(configs)
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