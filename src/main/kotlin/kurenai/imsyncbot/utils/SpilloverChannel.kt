package kurenai.imsyncbot.utils

import kotlinx.coroutines.channels.Channel

class SpilloverChannel<V> {

    private val mutex = WritePriorityRwMutex()
    private val bufferChannel = Channel<V>(200)

    suspend fun receive(): V {
        val res = bufferChannel.tryReceive()
        return if (res.isFailure) {
            mutex.write {

            }
        } else {
            res.getOrThrow()
        }
    }

    private suspend fun diskQueueIsEmpty(): Boolean {
        return true
    }

    private suspend fun fetchDiskQueue(): V {
        return
    }
}
