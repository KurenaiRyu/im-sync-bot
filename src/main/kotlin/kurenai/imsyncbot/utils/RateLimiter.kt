package kurenai.imsyncbot.utils

import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class RateLimiter(val permitsPerSecond: Long = 5L, private val bucketSize: Long = 20L) {

    private val log = KotlinLogging.logger {}

    var time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    private val rate = TimeUnit.SECONDS.toMillis(1) / permitsPerSecond
    private val lock = Object()

    fun acquire() {
        return acquire(1)
    }

    fun acquire(permits: Int) {
        acquire(permits, rate)
    }

    fun acquireForFile() {
        acquireForFile(1)
    }

    fun acquireForFile(permits: Int) {
        acquire(permits, TimeUnit.SECONDS.toMillis(5))
    }

    private fun acquire(permits: Int, rate: Long) {
        synchronized(lock) {
            val now = now()
            log.debug { "Has ${min((now - time) / this.rate, bucketSize)} tokens, acquire ${permits * rate / this.rate} tokens." }
            time = max(time, now - bucketSize * this.rate) + permits * rate
            if (time > now) {
                lock.wait(time - now)
            }
            log.debug { "Acquire successes, now has ${min((now() - time) / this.rate, bucketSize)} tokens." }
        }
    }

    private fun now(): Long {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    }

}