package kurenai.imsyncbot.cache

import mu.KotlinLogging
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.DelayQueue
import java.util.concurrent.TimeUnit

class Cache<K, V> @JvmOverloads constructor(private val name: String = "no name", private val size: Long = 0L) {
    private val log = KotlinLogging.logger {}
    private val cacheObjMap: ConcurrentMap<K, V> = ConcurrentHashMap()
    private val queue = DelayQueue<DelayItem<Pair<K, V>>>()
    private fun daemonCheck(name: String) {
        log.info { "$name cache service started." }
        while (true) {
            try {
                val delayItem = queue.take()
                // 超时对象处理
                val pair = delayItem.item
                cacheObjMap.remove(pair.first, pair.second) // compare and remove
            } catch (e: InterruptedException) {
                log.error(e) { e.message }
                break
            }
        }
        log.info { "$name cache service stopped." }
    }

    // 添加缓存对象
    fun put(key: K, value: V, time: Long, unit: TimeUnit?) {
        cacheObjMap.put(key, value)?.let { queue.remove<Any?>(key) }
        val milliTime = TimeUnit.MILLISECONDS.convert(time, unit)
        queue.put(DelayItem(Pair(key, value), milliTime))
        checkQueue()
    }

    // 添加缓存对象
    fun put(key: K, value: V, localDateTime: LocalDateTime) {
        if (cacheObjMap.put(key, value) != null) {
            queue.remove<Any?>(key)
        }
        queue.put(DelayItem(Pair(key, value), localDateTime))
        checkQueue()
    }

    fun remove(key: K) {
        cacheObjMap.remove(key)
    }

    fun removeAll() {
        cacheObjMap.clear()
    }

    operator fun get(key: K): V? {
        return cacheObjMap[key]
    }

    private fun checkQueue() {
        if (queue.size % 20 == 0) {
            log.debug { "$name queue size: ${queue.size}" }
        }
        if (size != 0L && queue.size > size) {
            val poll = queue.poll()
            log.debug { "$name cache over $size size. poll: ${poll.item.first} - ${poll.item.second}" }
        }
    }

    init {
        val daemonThread = Thread { daemonCheck(name) }
        daemonThread.isDaemon = true
        daemonThread.name = name
        daemonThread.start()
    }
}