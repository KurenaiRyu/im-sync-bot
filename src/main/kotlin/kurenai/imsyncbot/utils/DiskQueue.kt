package kurenai.imsyncbot.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.buffer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.io.path.exists
import kotlin.io.path.readText

class DiskQueue(
    folder: String,
    name: String,
) {

    private val parentPath = folder.toPath()
    private val queuePath = parentPath.resolve(name)
    private val metaPath = parentPath.resolve("$name.meta")
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private lateinit var metadata: QueueMetaData

    private val mutex = Mutex()
    private val fs = FileSystem.SYSTEM

    suspend fun init(): DiskQueue {
        fs.createDirectories(parentPath)
        if (fs.exists(metaPath)) {
            val metaData = json.decodeFromString(QueueMetaData.serializer(), queuePath.readText())
            if (metaData.lastCreateDateTime.isAfter(LocalDateTime.now().plus(-1, ChronoUnit.DAYS))) {
                this.metadata = metaData
            }
        }
        if (!::metadata.isInitialized) {
            this.metadata = QueueMetaData()
        }
        return this
    }

    suspend fun enqueue(bytes: ByteArray) = mutex.withLock {
        val sink = fs.sink(queuePath).buffer()
    }

    @Serializable
    private data class QueueMetaData(
        val offset: Long = 0,
        val writePos: Long = 0,
        val closed: Boolean = false,
        val lastCreatedTimestamp: Long = 0,
    ) {
        @Transient
        val lastCreateDateTime: LocalDateTime = if (lastCreatedTimestamp > 0) {
            LocalDateTime.ofEpochSecond(lastCreatedTimestamp, 0, ZoneOffset.of(ZoneId.systemDefault().id))
        } else {
            LocalDateTime.now()
        }
    }
}