package kurenai.imsyncbot.handler

interface Handler : Comparable<Handler> {

    companion object {
        const val CONTINUE = 1
        const val END = 2
    }

    fun order(): Int {
        return 100
    }

    override fun compareTo(other: Handler): Int {
        return this.order() - other.order()
    }

    fun handleName(): String {
        return this.javaClass.simpleName
    }

}