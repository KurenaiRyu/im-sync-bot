package kurenai.imsyncbot.config

import kotlinx.serialization.KSerializer
import kurenai.imsyncbot.utils.json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText

abstract class AbstractConfig<T> {

    abstract var configs: MutableList<T>
    protected abstract val path: Path

    protected abstract fun migration()
    abstract fun getConfigName(): String
    protected abstract fun refresh()

}