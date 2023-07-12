package kurenai.imsyncbot.config

import kotlinx.serialization.KSerializer
import kurenai.imsyncbot.configs
import kurenai.imsyncbot.utils.json
import java.nio.file.Path
import kotlin.io.path.*

abstract class AbstractConfig<T> {

    abstract val items: MutableList<T>
    protected abstract val serializer: KSerializer<List<T>>
    abstract val path: Path

    init {
        configs.add(this)
    }

    fun save() {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.createFile()
        }
        path.writeText(json.encodeToString(serializer, items))
        refresh()
    }

    fun reload() = load()

    fun load(file: Path = this.path) {
        if (file.exists()) {
            val configs = json.decodeFromString(serializer, file.readText())
            if (configs.isNotEmpty()) {
                items.clear()
                items.addAll(configs)
                refresh()
            }
        }
    }

    fun add(config: T) {
        add0(config)
        afterUpdate()
    }

    fun addAll(configs: Collection<T>) {
        addAll0(configs)
        afterUpdate()
    }

    protected open fun afterUpdate() {
        save()
    }

    protected abstract fun add0(config: T)
    protected abstract fun addAll0(configs: Collection<T>)
    abstract fun getConfigName(): String
    protected abstract fun refresh()

}