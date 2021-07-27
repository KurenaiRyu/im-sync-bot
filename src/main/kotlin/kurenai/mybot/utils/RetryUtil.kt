package kurenai.mybot.utils

import mu.KotlinLogging

object RetryUtil {

    private val log = KotlinLogging.logger {}

    private const val SLEEP = 200L
    private const val SLEEP_MAX = 3000L

    @Throws(Exception::class)
    fun <T> retry(retryTimes: Int, supplier: RetrySupplier<T>): T {
        var retryCount = 0
        while (retryCount <= retryTimes) {
            try {
                return supplier.get()
            } catch (e: Exception) {
                if (retryCount >= retryTimes) throw e
                log.warn("retry for exception: {}", e.message);
                retryCount++
            }
            Thread.sleep(Math.min(SLEEP * retryCount * retryCount * retryCount, SLEEP_MAX))
        }
        throw Exception("Over retry times.")
    }

    fun interface RetrySupplier<T> {
        @Throws(Exception::class)
        fun get(): T
    }
}