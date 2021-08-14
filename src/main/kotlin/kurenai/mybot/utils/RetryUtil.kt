package kurenai.mybot.utils

import kotlinx.coroutines.runBlocking
import kurenai.mybot.cache.DelayItem
import mu.KotlinLogging
import java.util.concurrent.DelayQueue

private val log = KotlinLogging.logger {}

// TODO 2021.8.13: 可能还是考虑用回调以及队列做重试会更加好
object RetryUtil {

    init {
        val daemonThread = Thread { retryDaemon() }
        daemonThread.isDaemon = true
        daemonThread.name = "Retry Daemon"
        daemonThread.start()
    }

    private const val MAX_TIMES = 5
    private val delayQueue = DelayQueue<DelayItem<RetryItem<*>>>()

    @Throws(Exception::class)
    suspend fun <T> aware(callable: SuspendCallable<T>, consumer: SuspendConsumer<T?, Throwable?>) {
        try {
            val result = callable.call()
            consumer.accept(result, null)
        } catch (e: Exception) {
            delayQueue.add(DelayItem(RetryItem(callable, consumer), getDelayTime(1)))
            log.error(e.message, e)
        }
    }

    private fun retryDaemon() {
        log.info("Retry daemon started.")
        var item: RetryItem<*>? = null
        while (true) {
            try {
                item = delayQueue.take().item
                runBlocking {
                    try {
                        item.callable.call()
                    } catch (e: Exception) {
                        if (item.count <= MAX_TIMES) {
                            item.count++
                            delayQueue.add(DelayItem(item, getDelayTime(item.count)))
                        } else {
                            runBlocking {
                                item.consumer.accept(null, e)
                            }
                        }
                    }
                }
            } catch (e: InterruptedException) {
                log.error("retry fail.", e)
                if (item != null) {
                    if (item.count <= MAX_TIMES) {
                        item.count++
                        delayQueue.add(DelayItem(item, getDelayTime(item.count)))
                    } else {
                        runBlocking {
                            item.consumer.accept(null, e)
                        }
                    }
                } else {
                    break
                }
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

    class RetryItem<T>(val callable: SuspendCallable<T>, val consumer: SuspendConsumer<T?, Throwable?>) {
        var count: Int = 0
    }
}
