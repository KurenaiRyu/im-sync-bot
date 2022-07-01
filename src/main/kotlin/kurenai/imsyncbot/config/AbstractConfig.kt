package kurenai.imsyncbot.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kurenai.imsyncbot.configs
import java.io.File

val configDefaultMapper: ObjectMapper = jacksonObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)

abstract class AbstractConfig<T> {

    open val mapper = configDefaultMapper
    abstract val items: ArrayList<T>
    protected abstract val typeRef: TypeReference<ArrayList<T>>
    protected abstract val file: File

    init {
        configs.add(this)
    }

    fun save() {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        mapper.writeValue(file, items)
        refresh()
    }

    fun reload() {
        load()
    }

    fun load() {
        if (file.exists()) {
            items.clear()
            items.addAll(mapper.readValue(file, typeRef))
            refresh()
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
        refresh()
    }

    protected abstract fun add0(config: T)
    protected abstract fun addAll0(configs: Collection<T>)
    abstract fun getConfigName(): String
    protected abstract fun refresh()

}