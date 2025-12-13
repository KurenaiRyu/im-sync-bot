package kurenai.imsyncbot.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kurenai.imsyncbot.exception.BotException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MessageDispatcher(
    private val parentScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val idleTimeoutMillis: Long = 60_000L,
    private val name: String = "MessageDispatcher-${count.getAndIncrement()}"
) {
    private data class Worker(
        val id: String,
        val channel: Channel<suspend () -> Unit>,
        val job: Job
    ) {
        var lastAccessTime: Long = 0L
    }

    private val workers = ConcurrentHashMap<String, Worker>()
    private val cleanerJob = parentScope.launch {
        delay(30_000)
        while (true) {
            runCatching {
                val now = System.currentTimeMillis()
                for (worker in workers.values) {
                    if (now - worker.lastAccessTime > idleTimeoutMillis && worker.channel.isEmpty) {
                        workers.remove(worker.id)
                        worker.channel.close()
                        log.debug("Clean worker({}-{})", name, worker.id)
                    } else {
                        log.debug(
                            "Worker({}-{}) remaining idle timeout is {}s, channel empty: {}",
                            name,
                            worker.id,
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

    tailrec suspend fun submit(id: String, task: suspend () -> Unit) {
        val worker = addWorkerIfNeed(id)
        log.debug("Worker({}-{}) reached full capacity", name, worker.id)
        worker.lastAccessTime = System.currentTimeMillis()
        try {
            return worker.channel.send(task)
        } catch (e: Exception) {
            throw BotException("Dispatch message($id) failed", e)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun addWorkerIfNeed(id: String): Worker = workers.compute(id) { key, prev ->
        if (prev != null && !(prev.channel.isClosedForSend)) return@compute prev

        val channel = Channel<suspend () -> Unit>(Channel.BUFFERED, BufferOverflow.SUSPEND)
        val job = parentScope.launch {
            for (f in channel) {
                runCatching { f() }.onFailure { log.error("{}-{} execute error: {}", name, id, it.message, it) }
            }
        }
        log.debug("New worker: {}-{}", name, id)
        Worker(id, channel, job).also { w ->
            channel.invokeOnClose {
                workers.remove(w.id, w)
            }
        }
    }!!


    companion object {
        private val log = getLogger()
        private val count by lazy { AtomicInteger(0) }
    }


}