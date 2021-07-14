package kurenai.mybot.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

public class Cache<K, V> {

    private final Logger                            log         = LoggerFactory.getLogger(Cache.class);
    private final ConcurrentMap<K, V>               cacheObjMap = new ConcurrentHashMap<>();
    private final DelayQueue<DelayItem<Pair<K, V>>> queue       = new DelayQueue<>();

    public Cache() {
        this("Cache");
    }

    public Cache(String name) {
        Thread daemonThread = new Thread(this::daemonCheck);
        daemonThread.setDaemon(true);
        daemonThread.setName(name + " Daemon");
        daemonThread.start();
    }

    private void daemonCheck() {
        log.info("cache service started.");
        for (; ; ) {
            try {
                DelayItem<Pair<K, V>> delayItem = queue.take();
                // 超时对象处理
                Pair<K, V> pair = delayItem.getItem();
                cacheObjMap.remove(pair.first, pair.second); // compare and remove
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
                break;
            }
        }
        log.info("cache service stopped.");
    }

    // 添加缓存对象
    @SuppressWarnings("SuspiciousMethodCalls")
    public void put(K key, V value, long time, TimeUnit unit) {
        if (cacheObjMap.put(key, value) != null) {
            queue.remove(key);
        }
        long nanoTime = TimeUnit.NANOSECONDS.convert(time, unit);
        queue.put(new DelayItem<>(new Pair<>(key, value), nanoTime));
    }

    // 添加缓存对象
    public void put(K key, V value, LocalDateTime localDateTime) {
        if (cacheObjMap.put(key, value) != null) {
            queue.remove(key);
        }
        queue.put(new DelayItem<>(new Pair<>(key, value), localDateTime));
    }

    public void remove(K key) {
        cacheObjMap.remove(key);
    }

    public void removeAll() {
        cacheObjMap.clear();
    }

    public V get(K key) {
        return cacheObjMap.get(key);
    }
}