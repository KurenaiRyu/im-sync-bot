package kurenai.imsyncbot.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kurenai.imsyncbot.exception.BotException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MessageDispatcher(
    private val parentScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val idleTimeoutMillis: Long = TimeUnit.MINUTES.toMillis(60),
    private val name: String = "MessageDispatcher-${count.getAndIncrement()}",
    private val capacityEachChannel: Int = 64
) {
    private data class Worker(
        val id: String,
        val name: String,
        val channel: Channel<suspend () -> Unit>,
        val job: Job
    ) {
        val nameWithId = if (id == name) id else "$name($id)"
        var lastAccessTime: Long = 0L
    }

    private val workers = ConcurrentHashMap<String, Worker>()
    private val cleanerJob = parentScope.launch {
        delay(60_000L)
        while (true) {
            runCatching {
                val now = System.currentTimeMillis()
                for (worker in workers.values) {
                    if (now - worker.lastAccessTime > idleTimeoutMillis && worker.channel.isEmpty) {
                        workers.remove(worker.id)
                        worker.channel.close()
                        log.debug("Clean worker({}-{})", name, worker.nameWithId)
                    } else {
                        log.debug(
                            "Worker({}-{}) remaining idle timeout is {}s, channel empty: {}",
                            name,
                            worker.nameWithId,
                            (worker.lastAccessTime + idleTimeoutMillis - now) / 1000.0,
                            worker.channel.isEmpty
                        )
                    }
                }
            }.onFailure {
                log.error("Error during cleanup worker", it)
            }
            delay(idleTimeoutMillis / 2)
        }
    }

    suspend fun submit(id: String, name: String = id, task: suspend () -> Unit) {
        val worker = addWorkerIfNeed(id, name)
        worker.lastAccessTime = System.currentTimeMillis()
        try {
            return worker.channel.send(task)
        } catch (e: Exception) {
            throw BotException("Dispatch message($id) failed", e)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun addWorkerIfNeed(id: String, name: String): Worker = workers.compute(id) { key, prev ->
        if (prev != null && !(prev.channel.isClosedForSend)) return@compute prev

        val channel = Channel<suspend () -> Unit>(Channel.BUFFERED, BufferOverflow.SUSPEND)
        val job = parentScope.launch {
            for (f in channel) {
                runCatching { f() }.onFailure { log.error("{}-{} execute error: {}", name, id, it.message, it) }
            }
        }
        Worker(id, name, channel, job).also { w ->
            channel.invokeOnClose {
                workers.remove(w.id, w)
            }
            log.debug("New worker: {}-{}", name, w.nameWithId)
        }
    }!!


    companion object {
        private val log = getLogger()
        private val count by lazy { AtomicInteger(0) }
    }


}