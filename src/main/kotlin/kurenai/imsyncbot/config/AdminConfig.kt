package kurenai.imsyncbot.config

import com.fasterxml.jackson.core.type.TypeReference
import kurenai.imsyncbot.ContextHolder
import java.io.File

object AdminConfig : AbstractConfig<Admin>() {

    val admin = ArrayList<Long>()
    val superAdmin = ArrayList<Long>()
    val master = ArrayList<Long>()
    override val configs = ArrayList<Admin>()
    override val file = File("./config/admin.json")
    override val typeRef = object : TypeReference<ArrayList<Admin>>() {}

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            save()
        })
        load()
    }

    fun add(id: Long, superAdmin: Boolean = false) {
        add(Admin(id, superAdmin))
    }

    fun remove(admin: Long) {
        configs.removeIf { it.id == admin }
        afterUpdate()
    }

    override fun refresh() {
        admin.clear()
        admin.addAll(configs.map { it.id })
        superAdmin.addAll(configs.filter { it.superAdmin }.map { it.id })

        master.addAll(ContextHolder.masterOfTg)
        admin.addAll(ContextHolder.masterOfTg)
        superAdmin.addAll(ContextHolder.masterOfTg)
    }

    override fun add0(config: Admin) {
        configs.removeIf { config.id == it.id }
        configs.add(config)
    }

    override fun addAll0(configs: Collection<Admin>) {
        for (config in configs) {
            add0(config)
        }
    }

    override fun getConfigName(): String {
        return "管理员配置"
    }
}

data class Admin(
    val id: Long,
    val superAdmin: Boolean = false,
)