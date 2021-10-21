package kurenai.imsyncbot.utils.downloader

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

abstract class Downloader(
    val url: String,
    val file: File,
    val offset: Long = 0L,
) : Thread() {

    companion object {
        const val BYTE_ARRAY_LENGTH = 8 * 1024
    }

    fun writeToFile(inputStream: InputStream) {
        val b = ByteArray(BYTE_ARRAY_LENGTH)
        var readBytes: Int
        var tmpOffset = offset
        inputStream.use { bis ->
            RandomAccessFile(file, "rw").use { raf ->
                while (bis.read(b).also { readBytes = it } != -1) {
                    raf.seek(tmpOffset)
                    raf.write(b, 0, readBytes)
                    tmpOffset += readBytes
                }
            }
        }
    }

}