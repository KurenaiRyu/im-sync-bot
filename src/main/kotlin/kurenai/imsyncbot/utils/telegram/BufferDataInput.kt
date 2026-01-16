package kurenai.imsyncbot.utils.telegram

import okio.Buffer
import java.io.DataInput
import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * DataInput implementation backed by an Okio BufferedSource.
 *
 * Notes:
 * - Uses big-endian for multibyte primitives (matches DataInputStream).
 * - readUTF reads an unsigned short length then that many bytes and decodes UTF-8
 *   (this is NOT Java's "modified UTF-8").
 */
class BufferDataInput(private val buffer: Buffer) : DataInput {

    @Throws(IOException::class)
    override fun readFully(b: ByteArray) {
        readFully(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun readFully(b: ByteArray, off: Int, len: Int) {
        if (len < 0) throw IndexOutOfBoundsException("len < 0")
        var read = 0
        while (read < len) {
            val r = buffer.read(b, off + read, len - read)
            if (r == -1) throw EOFException()
            read += r
        }
    }

    @Throws(IOException::class)
    override fun skipBytes(n: Int): Int {
        if (n <= 0) return 0
        var remaining = n
        val buf = ByteArray(minOf(8192, n))
        while (remaining > 0) {
            val toRead = minOf(buf.size, remaining)
            val r = buffer.read(buf, 0, toRead)
            if (r == -1) break
            remaining -= r
        }
        return n - remaining
    }

    @Throws(IOException::class)
    override fun readBoolean(): Boolean = readByte() != 0.toByte()

    @Throws(IOException::class)
    override fun readByte(): Byte = buffer.readByte()

    @Throws(IOException::class)
    override fun readUnsignedByte(): Int = buffer.readByte().toInt() and 0xFF

    @Throws(IOException::class)
    override fun readShort(): Short = buffer.readShort()

    @Throws(IOException::class)
    override fun readUnsignedShort(): Int = buffer.readShort().toInt() and 0xFFFF

    @Throws(IOException::class)
    override fun readChar(): Char = readUnsignedShort().toChar()

    @Throws(IOException::class)
    override fun readInt(): Int = buffer.readInt()

    @Throws(IOException::class)
    override fun readLong(): Long = buffer.readLong()

    @Throws(IOException::class)
    override fun readFloat(): Float = Float.fromBits(readInt())

    @Throws(IOException::class)
    override fun readDouble(): Double = Double.fromBits(readLong())

    /**
     * Deprecated in DataInput. Provide a simple implementation.
     */
    @Throws(IOException::class)
    override fun readLine(): String? {
        val sb = StringBuilder()
        var seenAny = false
        while (true) {
            val bInt: Int = try {
                buffer.readByte().toInt() and 0xFF
            } catch (e: EOFException) {
                break
            }
            seenAny = true
            when (bInt) {
                '\n'.code -> break
                '\r'.code -> {
                    // try to consume following '\n' if present
                    try {
                        val next = buffer.readByte().toInt() and 0xFF
                        if (next != '\n'.code) {
                            // cannot unread; ignore (deprecated method)
                        }
                    } catch (ignored: EOFException) {
                    }
                    break
                }

                else -> sb.append(bInt.toChar())
            }
        }
        return if (seenAny) sb.toString() else null
    }

    @Throws(IOException::class)
    override fun readUTF(): String {
        val utflen = readUnsignedShort()
        if (utflen == 0) return ""
        val bytes = ByteArray(utflen)
        readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }
}