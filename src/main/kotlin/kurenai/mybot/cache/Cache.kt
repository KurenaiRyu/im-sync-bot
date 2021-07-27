package kurenai.mybot.cache

import mu.KotlinLogging
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.DelayQueue
import java.util.concurrent.TimeUnit

class Cache<K, V> @JvmOverloads constructor(name: String = "Cache") {
    private val log = KotlinLogging.logger {}
    private val cacheObjMap: ConcurrentMap<K, V> = ConcurrentHashMap()
    private val queue = DelayQueue<DelayItem<Pair<K, V>>>()
    private fun daemonCheck() {
        log.info("cache service started.")
        while (true) {
            try {
                val delayItem = queue.take()
                // 超时对象处理
                val pair = delayItem.item
                cacheObjMap.remove(pair.first, pair.second) // compare and remove
            } catch (e: InterruptedException) {
                log.error(e.message, e)
                break
            }
        }
        log.info("cache service stopped.")
    }

    // 添加缓存对象
    fun put(key: K, value: V, time: Long, unit: TimeUnit?) {
        cacheObjMap.put(key, value)?.let { queue.remove<Any?>(key) }
        val nanoTime = TimeUnit.NANOSECONDS.convert(time, unit)
        queue.put(DelayItem(Pair(key, value), nanoTime))
    }

    // 添加缓存对象
    fun put(key: K, value: V, localDateTime: LocalDateTime) {
        if (cacheObjMap.put(key, value) != null) {
            queue.remove<Any?>(key)
        }
        queue.put(DelayItem(Pair(key, value), localDateTime))
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

    init {
        val daemonThread = Thread { daemonCheck() }
        daemonThread.isDaemon = true
        daemonThread.name = "$name Daemon"
        daemonThread.start()
    }
}