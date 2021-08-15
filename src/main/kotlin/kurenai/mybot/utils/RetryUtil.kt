package kurenai.mybot.utils

import kotlinx.atomicfu.locks.withLock
import kurenai.mybot.cache.DelayItem
import mu.KotlinLogging
import java.util.concurrent.DelayQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

private val log = KotlinLogging.logger {}

object RetryUtil {

    init {
        val daemonThread = Thread { retryDaemon() }
        daemonThread.isDaemon = true
        daemonThread.name = "Retry Daemon"
        daemonThread.start()
    }

    private const val MAX_TIMES = 3
    private val delayQueue = DelayQueue<DelayItem<Condition>>()

    @Throws(Exception::class)
    suspend fun <T> aware(callable: SuspendCallable<T>, consumer: SuspendConsumer<T?, Throwable?>) {
        try {
            val result = callable.call()
            consumer.accept(result, null)
        } catch (e: Exception) {
            val lock = ReentrantLock()
            val cond = lock.newCondition()
            delayQueue.add(DelayItem(cond, getDelayTime(1)))
            doRetry(callable, consumer, lock, cond)
            log.error(e.message, e)
        }
    }

    private suspend fun <T> doRetry(
        callable: SuspendCallable<T>,
        consumer: SuspendConsumer<T?, Throwable?>,
        lock: ReentrantLock,
        cond: Condition,
        count: Int = 1
    ) {
        lock.withLock {
            try {
                cond.await(getDelayTime(MAX_TIMES) + 5000L, TimeUnit.MILLISECONDS)
                val result = callable.call()
                consumer.accept(result, null)
            } catch (e: Exception) {
                log.error("Retry fail $count time(s).", e)
                val nextCount = count + 1
                if (MAX_TIMES >= nextCount) {
                    delayQueue.add(DelayItem(cond, getDelayTime(nextCount)))
                    doRetry(callable, consumer, lock, cond, nextCount)
                } else {
                    log.error("Retry over $MAX_TIMES times. Discard.")
                    consumer.accept(null, e)
                }
            }
        }
    }

    private fun retryDaemon() {
        log.info("Retry daemon started.")
        while (true) {
            try {
                delayQueue.take().item.signal()
            } catch (e: InterruptedException) {
                log.error("Retry daemon run fail.", e)
                break
            }
        }
        log.info("Retry daemon stopped.")
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
