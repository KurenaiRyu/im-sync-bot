package kurenai.imsyncbot.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kurenai.imsyncbot.exception.BotException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MessageDispatcher(
    private val parentScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    idleTimeout: Duration = 60.seconds,
    private val name: String = "MessageDispatcher-${count.getAndIncrement()}",
    private val capacityEachChannel: Int = 64
) {
    private val idleTimeoutMillis: Long = idleTimeout.inWholeMilliseconds
    private data class Worker(
        val id: String,
        val name: String,
        val channel: Channel<suspend () -> Unit>,
        val job: Job
    ) {
        val nameWithId = if (id == name) id else "$name($id)"
        var lastAccessTime: Long = 0
    }

    private val workers = ConcurrentHashMap<String, Worker>()
    private val cleanerJob = parentScope.launch {
        delay(60.seconds)
        while (true) {
            runCatching {
                val now = System.currentTimeMillis()
                for (worker in workers.values) {
                    if (now - worker.lastAccessTime > idleTimeout.inWholeMilliseconds && worker.channel.isEmpty) {
                        workers.remove(worker.id)
                        worker.channel.close()
                        log.debug("Clean worker({}-{})", name, worker.nameWithId)
                    } else {
                        log.debug(
                            "Worker({}-{}) remaining idle timeout is {}s, channel empty: {}",
                            name,
                            worker.nameWithId,
                            ((worker.lastAccessTime + idleTimeout.inWholeMilliseconds - now) / 1000.0).coerceAtLeast(0.0),
                            worker.channel.isEmpty
                        )
                    }
                }
            }.onFailure {
                log.error("Error during cleanup worker", it)
            }
            delay(idleTimeout / 2)
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

        val channel = Channel<suspend () -> Unit>(capacityEachChannel, BufferOverflow.SUSPEND)
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