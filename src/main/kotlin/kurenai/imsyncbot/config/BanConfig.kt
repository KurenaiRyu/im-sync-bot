package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import java.io.File

object BanConfig : AbstractConfig<Ban>() {

    val banedIp = ArrayList<Long>()
    val banedGroup = ArrayList<Long>()
    override val configs: ArrayList<Ban> = ArrayList()
    override val typeRef = object : TypeReference<ArrayList<Ban>>() {}
    override val file: File = File("./config/ban.json")

    override fun add0(config: Ban) {
        if (configs.size == 0) configs.add(config)
        else {
            configs.firstOrNull { c -> config.id?.let { c.id == it } ?: (config.group == c.group) } ?: kotlin.run {
                configs.add(config)
            }
        }
    }

    fun addGroup(group: Long) {
        add(Ban(group = group))
    }

    fun addId(id: Long) {
        add(Ban(id))
    }

    fun removeGroup(group: Long) {
        configs.removeIf { it.group == group }
        afterUpdate()
    }

    fun removeId(id: Long) {
        configs.removeIf { it.id == id }
        afterUpdate()
    }

    override fun addAll0(configs: Collection<Ban>) {
        for (config in configs) {
            add0(config)
        }
    }

    override fun getConfigName(): String {
        return "黑名单配置"
    }

    override fun refresh() {
        for (config in configs) {
            config.id?.let(banedIp::add)
            config.group?.let(banedGroup::add)
        }
    }
}

data class Ban(
    val id: Long? = null,
    val group: Long? = null,
)