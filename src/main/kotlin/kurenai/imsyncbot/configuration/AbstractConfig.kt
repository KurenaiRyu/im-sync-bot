package kurenai.imsyncbot.configuration

abstract class AbstractConfig<T> {

    abstract var configs: MutableList<T>

    abstract fun getConfigName(): String
    protected abstract suspend fun refresh()

}