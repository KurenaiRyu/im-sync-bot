package kurenai.mybot.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

object RetryUtil {

    private const val MAX_TIMES = 5

    @Throws(Exception::class)
    suspend fun <T> retry(id: Int, callable: SuspendCallable<T>): T {
        return try {
            callable.call()
        } catch (e: Exception) {
            doRetry(callable, 1, id)
        }
    }

    @Throws(Exception::class)
    suspend fun <T> doRetry(callable: SuspendCallable<T>, times: Int, id: Int): T {
        return try {
            withContext(Dispatchers.IO) {
                Thread.sleep(getDelayTime(times))
            }
            log.warn("Retry for id[$id] $times time(s)")
            return callable.call()
        } catch (e: Exception) {
            if (MAX_TIMES >= times) {
                doRetry(callable, times + 1, id)
            } else {
                log.error("Retry over $times time(s)")
                throw e
            }
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