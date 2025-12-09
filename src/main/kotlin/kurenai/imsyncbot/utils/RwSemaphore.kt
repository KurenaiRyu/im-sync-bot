package kurenai.imsyncbot.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WritePriorityRwMutex(
    permits: Int = 1,
) {

    private val stateMutex = Mutex()

    private val readGate = Mutex()
    private val writeGate = Mutex()

    private var readers = 0
    private var writers = 0
    private var waitingWriters = 0

    suspend fun <T> read(block: suspend () -> T): T {
        readGate.withLock { stateMutex.withLock {
            readers++
            if (readers == 1) {
                writeGate.lock()
            }
        } }

        try {
            return block()
        } finally {
            stateMutex.withLock {
                readers--
                if (readers == 0) {
                    writeGate.unlock()
                }
            }
        }
    }

    suspend fun write(block: suspend () -> Unit) {
        stateMutex.withLock {
            waitingWriters++
            if (waitingWriters == 1) {
                readGate.lock()
            }
        }

        writeGate.lock()
        stateMutex.withLock {
            waitingWriters--
            writers++
        }

        try {
            return block()
        } finally {
            stateMutex.withLock {
                writers--
                if (waitingWriters == 0) {
                    readGate.unlock()
                }
            }
            writeGate.unlock()
        }
    }

}