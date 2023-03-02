package kurenai.imsyncbot.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kurenai.imsyncbot.configs
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

val configDefaultMapper: ObjectMapper = jacksonObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)

abstract class AbstractConfig<T> {

    open val mapper = configDefaultMapper
    abstract val items: ArrayList<T>
    protected abstract val typeRef: TypeReference<ArrayList<T>>
    abstract val path: Path

    init {
        configs.add(this)
    }

    fun save() {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.createFile()
        }
        mapper.writeValue(path.toFile(), items)
        refresh()
    }

    fun reload() = load()

    fun load(file: Path = this.path) {
        if (file.exists()) {
            val configs = mapper.readValue(file.toFile(), typeRef)
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