package kurenai.imsyncbot.utils

import kotlinx.coroutines.delay
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

object RetryUtil {

    private const val MAX_TIMES = 1

    @Throws(Exception::class)
    suspend fun <T> aware(callable: SuspendCallable<T>, consumer: SuspendConsumer<T?, Throwable?>) {
        try {
            val result = callable.call()
            consumer.accept(result, null)
        } catch (e: Exception) {
            doRetry(callable, consumer)
            log.error(e) {
                e.message
            }
        }
    }

    private suspend fun <T> doRetry(
        callable: SuspendCallable<T>,
        consumer: SuspendConsumer<T?, Throwable?>,
    ) {
        var count = 1
        var isDone = false
        while (!isDone)
            try {
                delay(getDelayTime(count))
                val result = callable.call()
                consumer.accept(result, null)
                isDone = true
            } catch (e: Exception) {
                log.error { "Retry fail $count times. ${e.message}" }
                count++
                if (MAX_TIMES < count) {
                    log.error { "Retry over $MAX_TIMES times. Discard." }
                    consumer.accept(null, e)
                    isDone = true
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

    @FunctionalInterface
    fun interface SuspendConsumer<T, U> {
        suspend fun accept(t: T, u: U)
    }
}
