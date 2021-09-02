package kurenai.imsyncbot.cache

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.validation.constraints.NotNull

open class DelayItem<T> : Delayed {
    /**
     * Sequence number to break ties FIFO
     */
    private val sequenceNumber: Long

    /**
     * The time the task is enabled to execute in nanoTime units
     */
    private val time: Long
    val item: T

    constructor(submit: T, timeout: Long) {
        time = now() + timeout * 1000000
        item = submit
        sequenceNumber = sequencer.getAndIncrement()
    }

    constructor(submit: T, expiration: LocalDateTime) {
        time = (expiration.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() * 1000000
                - NANO_ORIGIN)
        item = submit
        sequenceNumber = sequencer.getAndIncrement()
    }

    override fun getDelay(unit: TimeUnit): Long {
        return unit.convert(time - now(), TimeUnit.NANOSECONDS)
    }

    override fun compareTo(other: @NotNull Delayed?): Int {
        if (other === this) { // compare zero ONLY if same object
            return 0
        }
        if (other is DelayItem<*>) {
            val diff = time - other.time
            return when {
                diff < 0 -> {
                    -1
                }
                diff > 0 -> {
                    1
                }
                sequenceNumber < other.sequenceNumber -> {
                    -1
                }
                else -> {
                    1
                }
            }
        }
        val d = getDelay(TimeUnit.NANOSECONDS) - other!!.getDelay(TimeUnit.NANOSECONDS)
        return if (d == 0L) 0 else if (d < 0) -1 else 1
    }

    companion object {
        /**
         * Base of nanosecond timings, to avoid wrapping
         */
        private val NANO_ORIGIN = System.nanoTime()

        /**
         * Returns nanosecond time offset by origin
         */
        fun now(): Long {
            return System.nanoTime() - NANO_ORIGIN
        }

        /**
         * Sequence number to break scheduling ties, and in turn to guarantee FIFO order among tied entries.
         */
        private val sequencer = AtomicLong(0)
    }
}
