package kurenai.imsyncbot.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import kotlin.math.min

class SpilloverChannel<V> (
    val name: String,
    val serializer: Serializer<V>,
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
        diskQueue.enqueue(serializer.serialize(value))
    }

    private suspend fun restoreFromDiskIfNeed() = stateLock.withLock {
        if (bufferChannel.isEmpty && diskQueue.state.value == DiskQueue.State.NORMAL) {
            diskQueue.dequeue(fetchDiskQueueNum).getOrNull()?.mapNotNull {
                serializer.deserialize(it)
            }?.forEach {
                bufferChannel.send(it)
            }
        }
    }

    override fun close() {
        diskQueue.close()
        bufferChannel.close()
    }

    interface Serializer<V> {
        fun serialize(value: V): ByteString
        fun deserialize(byteString: ByteString): V?
    }
}

fun <V> buildSerialize(clazz: Class<V>) = object : SpilloverChannel.Serializer<V> {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter: JsonAdapter<V> = moshi.adapter(clazz)

    override fun serialize(value: V): ByteString {
        return adapter.toJson(value).encodeUtf8()
    }

    override fun deserialize(byteString: ByteString): V? {
        return runCatching {
            adapter.fromJson(byteString.utf8())
        }.getOrNull()
    }
}
