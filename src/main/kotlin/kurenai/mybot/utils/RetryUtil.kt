package kurenai.mybot.utils

import kurenai.mybot.cache.DelayItem
import mu.KotlinLogging
import java.util.concurrent.DelayQueue
import java.util.concurrent.atomic.AtomicLong

private val log = KotlinLogging.logger {}

object RetryUtil {

    private const val MAX_TIMES = 3
    private val id = AtomicLong()

    private val queue: DelayQueue<DelayItem<SuspendCallable<*>>> = DelayQueue()

    @Throws(Exception::class)
    suspend fun <T> retry(callable: SuspendCallable<T>): T {
        return try {
            callable.call()
        } catch (e: Exception) {
            queue.add(DelayItem(callable, getDelayTime(1)))
            doRetry(callable, 1, id.getAndIncrement())
        }
    }

    @Throws(Exception::class)
    suspend fun <T> doRetry(callable: SuspendCallable<T>, times: Int, id: Long): T {
        try {
            log.warn("Retry for id[$id] $times time(s)")
            return queue.take().item.call() as T
        } catch (e: Exception) {
            if (MAX_TIMES >= times) {
                doRetry(callable, times + 1, id)
            }
            log.error("Retry over $times time(s)")
            throw e
        }
    }

    private fun getDelayTime(times: Int): Long {
        return when (times) {
            1 -> 500L
            2 -> 2000L
            3 -> 10000L
            else -> 30000L
        }
    }

    @FunctionalInterface
    fun interface SuspendCallable<V> {
        /**
         * Computes a result, or throws an exception if unable to do so.
         *
         * @return computed result
         * @throws Exception if unable to compute a result
         */
        @Throws(Exception::class)
        suspend fun call(): V
    }

}