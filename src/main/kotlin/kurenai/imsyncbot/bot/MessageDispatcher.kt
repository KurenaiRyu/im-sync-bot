package kurenai.imsyncbot.bot

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.utils.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class MessageDispatcher(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
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
    private val cleanerJob = scope.launch {
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

    tailrec fun submit(id: String, task: suspend () -> Unit) {
        val worker = addWorkerIfNeed(id)
        worker.lastAccessTime = System.currentTimeMillis()
        val result = worker.channel.trySend(task)
        if (result.isClosed) {
            submit(id, task)
        } else if (result.isFailure) {
            throw BotException("Dispatch message($id) failed", result.exceptionOrNull())
        }
    }

    private fun addWorkerIfNeed(id: String) = workers.computeIfAbsent(id) {
        val channel = Channel<suspend () -> Unit>(Channel.BUFFERED, BufferOverflow.SUSPEND)
        val job = scope.launch {
            for (f in channel) {
                runCatching { f() }.onFailure { log.error("{}-{} execute error: {}", name, id, it.message, it) }
            }
        }
        log.debug("New worker: {}-{}", name, id)
        Worker(id, channel, job)
    }


    companion object {
        private val log = getLogger()
        private val count = atomic(0L)
    }


}