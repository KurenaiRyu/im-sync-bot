package kurenai.mybot.utils

import mu.KotlinLogging
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

object RetryUtil {

    private val log = KotlinLogging.logger {}
    private val threadNumber = AtomicInteger(1)
    private val pool = Executors.newScheduledThreadPool(10) {
        val t = Thread(Thread.currentThread().threadGroup, it,
                "retry-thread-" + threadNumber.getAndIncrement(),
                0)
        if (t.isDaemon) t.isDaemon = false
        if (t.priority != Thread.NORM_PRIORITY) t.priority = Thread.NORM_PRIORITY
        t
    }

    private const val DELAY = 200L
    private const val DELAY_MAX = 3000L

    @Throws(Exception::class)
    suspend fun <T> retry(retryTimes: Int, supplier: RetrySupplier<T>): T {
        val retryCount = 1

        try {
            return supplier.get()
        } catch (e: Exception) {
            pool.schedule({
                suspend {
                    doRetry(retryCount + 1, retryTimes, supplier)
                }
            }, getDelay(retryCount), TimeUnit.MILLISECONDS)
            throw e
        }
    }

    private suspend fun <T> doRetry(retryCount: Int, retryTimes: Int, supplier: RetrySupplier<T>) {
        try {
            supplier.get()
        } catch (e: Exception) {
            if (retryCount >= retryTimes) throw e
            log.warn("retry for exception: {}", e.message);
            pool.schedule({ suspend { doRetry(retryCount + 1, retryTimes, supplier) } }, getDelay(retryCount), TimeUnit.MILLISECONDS)
        }
    }

    private fun getDelay(retryCount: Int): Long {
        if (retryCount < 1) return DELAY
        return min(DELAY * retryCount * retryCount, DELAY_MAX)
    }

    fun interface RetrySupplier<T> {
        @Throws(Exception::class)
        suspend fun get(): T
    }
}