package kurenai.imsyncbot.utils

import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class RateLimiter(
    private val lock: Object,
    private val name: String = "RateLimiter",
    permitsPerSecond: Double = 0.5,
    private val bucketSize: Long = 1,
    private var time: Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
    private val rate: Long = (TimeUnit.SECONDS.toMillis(1) / permitsPerSecond).toLong(),
) {

    private val log = KotlinLogging.logger {}

    fun acquire() {
        return acquire(1)
    }

    fun acquire(permits: Int) {
        acquire(permits, rate)
    }

    fun <T> acquire(supplier: () -> T): T {
        return acquire(1, supplier)
    }

    fun <T> acquire(permits: Int, supplier: () -> T): T {
        synchronized(lock) {
            val ret: T = supplier()
            acquire(permits, rate)
            return ret
        }
    }

    private fun acquire(permits: Int, rate: Long) {
        log.debug { "$name has ${min((now() - time) / this.rate, bucketSize)} tokens, acquire ${permits * rate / this.rate} tokens." }
        synchronized(lock) {
            val now = now()
            time = max(time, now - bucketSize * this.rate) + permits * rate
            if (time > now) {
                lock.wait(time - now)
            }
            log.debug { "Acquire successes, now has ${min((now() - time) / this.rate, bucketSize)} tokens." }
        }
    }

    fun now(): Long {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    }

}