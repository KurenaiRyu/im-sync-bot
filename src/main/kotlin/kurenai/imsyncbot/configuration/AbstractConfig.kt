package kurenai.imsyncbot.configuration

import java.nio.file.Path

abstract class AbstractConfig<T> {

    abstract var configs: MutableList<T>
    protected abstract val path: Path

    protected abstract fun migration()
    abstract fun getConfigName(): String
    protected abstract fun refresh()

}