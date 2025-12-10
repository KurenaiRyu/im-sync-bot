package kurenai.imsyncbot.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.ByteString
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.slf4j.LoggerFactory
import java.io.Closeable

class DiskQueue(
    folder: String,
    name: String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
): Closeable {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val parentPath = folder.toPath()
    private val queuePath = parentPath.resolve(name)
    private val metaPath = parentPath.resolve("$name.meta")

    private val lock = Mutex()
    private val fs = FileSystem.SYSTEM
    private val queueHandle = fs.openReadWrite(queuePath)
    private val metaHandle = fs.openReadWrite(metaPath)

    private lateinit var metadata: QueueMetaData
    private val _state = MutableStateFlow<State>(State.Init)
    val state = _state.asStateFlow()
    val dataFlow = flow {
        while (true) {
            state.filter { it == State.NORMAL }.first()
            emit(dequeue())
        }
    }

    suspend fun init(): DiskQueue = lock.withLock {
        if (_state.value != State.Init) return this
        fs.createDirectories(parentPath)
        if (fs.exists(metaPath)) {
            val content = fs.read(metaPath) {
                readByteString().string(Charsets.UTF_8)
            }
            val metaData = json.decodeFromString(QueueMetaData.serializer(), content)
            if (hasNext()) {
                this.metadata = metaData
            }
        }
        if (!::metadata.isInitialized) {
            this.metadata = QueueMetaData()
        }
        updateState(State.Ready)
        return this
    }

    suspend fun enqueue(item: ByteString) = lock.withLock {
        queueHandle.sink(metadata.writePos).buffer().use { sink ->
            doSingleEnqueue(sink, item)
            metadata.writePos = queueHandle.position(sink)
        }
        updateState(State.NORMAL)
    }

    suspend fun enqueue(itemArray: Array<ByteString>) = lock.withLock {
        queueHandle.sink(metadata.writePos).buffer().use { sink ->
            for (bytes in itemArray) {
                doSingleEnqueue(sink, bytes)
            }
            metadata.writePos = queueHandle.position(sink)
        }
        updateState(State.NORMAL)
    }

    suspend fun dequeue(offset: Long = metadata.offset): Result<ByteString?> {
        return runCatching {
            lock.withLock {
                if (!hasNext()) {
                    updateState(State.Empty)
                    return@withLock null
                }

                queueHandle.source(offset).buffer().use { source ->
                    val len = source.readInt()
                    val crc = source.readInt()
                    val bytes = source.readByteString(len.toLong())
                    if (bytes.crc32c() != crc) {
                        throw IllegalStateException("checksum mismatch")
                    }
                    metadata.offset = queueHandle.position(source)
                    if (!hasNext()) {
                        metadata.reset()
                        updateState(State.Empty)
                    }
                    bytes
                }
            }
        }.onFailure {
            log.warn("Failed to dequeue, reset metadata", it)
            metadata.reset()
        }
    }

    suspend fun dequeue(n: Int, offset: Long = metadata.offset): Result<List<ByteString>> {
        return runCatching {
            lock.withLock {
                if (!hasNext()) {
                    updateState(State.Empty)
                    return@withLock emptyList()
                }

                queueHandle.source(offset).buffer().use { source ->
                    val res = mutableListOf<ByteString>()
                    val max = if (n == -1) Int.MAX_VALUE else n
                    var count = 0

                    runCatching {
                        do {
                                val len = source.readInt()
                                val crc = source.readInt()
                                val bytes = source.readByteString(len.toLong())
                                if (bytes.crc32c() != crc) {
                                    log.warn("checksum mismatch, reset metadata")
                                    metadata.reset()
                                }
                                res.add(bytes)
                                count++
                        } while (count < max && hasNext().also {
                                if (!it) metadata.reset()
                            })
                    }.onSuccess {
                        metadata.offset = queueHandle.position(source)
                    }.onFailure {
                        log.warn("Failed to dequeue, reset metadata", it)
                        metadata.reset()
                    }.getOrNull()
                    res
                }
            }
        }.onFailure {
            log.warn("Failed to dequeue, reset metadata", it)
            metadata.reset()
        }
    }

    private fun doSingleEnqueue(sink: BufferedSink, bytes: ByteString) {
        sink.writeInt(bytes.size)
        sink.writeInt(bytes.crc32c())
        sink.write(bytes)
    }

    private fun hasNext(): Boolean {
        return metadata.offset + Int.SIZE_BYTES * 2 < metadata.writePos
    }

    override fun close() {
        queueHandle.close()
        metaHandle.close()
        fs.close()
    }

    @Serializable
    private data class QueueMetaData(
        var offset: Long = 0,
        var writePos: Long = 0,
    ) {

        fun reset() {
            offset = 0
            writePos = 0
        }
    }

    private fun updateState(state: State) {
        _state.update { prev ->
            when (state) {
                State.Init -> state
                State.Ready -> state
                State.Empty, State.NORMAL -> {
                    if (prev != State.Init) state
                    else prev
                }
            }
        }
    }

    sealed interface State {
        object Init: State
        object Ready: State
        object Empty: State
        object NORMAL: State
    }
}