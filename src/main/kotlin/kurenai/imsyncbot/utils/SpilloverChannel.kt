package kurenai.imsyncbot.utils

import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.math.min

class SpilloverChannel<V> (
    val name: String,
    val serializer: KSerializer<V>,
    val capacity: Int = 64,
    val folder: String = "./messages",
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
): Closeable {

    private val lock = WritePriorityRwMutex()
    private val stateLock = Mutex()
    private val diskQueue = DiskQueue(folder, name, json)
    private val bufferChannel = Channel<V>(capacity)
    private val fetchDiskQueueNum = min(capacity, 20)

    suspend fun receive(): V = lock.withRead {
        restoreFromDiskIfNeed()
        bufferChannel.receive()
    }

    suspend fun tryReceive(): ChannelResult<V> = lock.withRead {
        restoreFromDiskIfNeed()
        bufferChannel.tryReceive()
    }

    suspend fun send(value: V) = lock.withWrite {
        val res = bufferChannel.trySend(value)
        if (res.isSuccess) return@withWrite
        diskQueue.enqueue(json.encodeToString(serializer, value).encodeUtf8())
    }

    private suspend fun restoreFromDiskIfNeed() = stateLock.withLock {
        if (bufferChannel.isEmpty && diskQueue.state.value == DiskQueue.State.NORMAL) {
            diskQueue.dequeue(fetchDiskQueueNum).getOrNull()?.map {
                json.decodeFromString(serializer, it.utf8())
            }?.forEach {
                bufferChannel.send(it)
            }
        }
    }

    override fun close() {
        diskQueue.close()
        bufferChannel.close()
    }
}
