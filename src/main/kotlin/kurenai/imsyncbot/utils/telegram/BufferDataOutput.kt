package com.tdlight.io

import okio.Buffer
import java.io.DataOutput
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * DataOutput implementation backed by an Okio BufferedSink.
 *
 * Notes:
 * - Uses big-endian for multibyte primitives (matches DataOutputStream).
 * - writeUTF writes a 2-byte unsigned length (max 65535) then that many UTF-8 bytes
 *   (this is NOT Java's "modified UTF-8").
 */
class BufferDataOutput(private val buffer: Buffer) : DataOutput {

    @Throws(IOException::class)
    override fun write(b: Int) {
        buffer.writeByte(b)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        buffer.write(b)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        buffer.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun writeBoolean(v: Boolean) {
        buffer.writeByte(if (v) 1 else 0)
    }

    @Throws(IOException::class)
    override fun writeByte(v: Int) {
        buffer.writeByte(v)
    }

    @Throws(IOException::class)
    override fun writeShort(v: Int) {
        buffer.writeShort(v)
    }

    @Throws(IOException::class)
    override fun writeChar(v: Int) {
        buffer.writeShort(v)
    }

    @Throws(IOException::class)
    override fun writeInt(v: Int) {
        buffer.writeInt(v)
    }

    @Throws(IOException::class)
    override fun writeLong(v: Long) {
        buffer.writeLong(v)
    }

    @Throws(IOException::class)
    override fun writeFloat(v: Float) {
        buffer.writeInt(java.lang.Float.floatToIntBits(v))
    }

    @Throws(IOException::class)
    override fun writeDouble(v: Double) {
        buffer.writeLong(java.lang.Double.doubleToLongBits(v))
    }

    @Throws(IOException::class)
    override fun writeBytes(s: String) {
        val bytes = s.toByteArray(StandardCharsets.ISO_8859_1)
        buffer.write(bytes)
    }

    @Throws(IOException::class)
    override fun writeChars(s: String) {
        for (ch in s) {
            buffer.writeShort(ch.code)
        }
    }

    @Throws(IOException::class)
    override fun writeUTF(s: String) {
        val utf8 = s.toByteArray(StandardCharsets.UTF_8)
        if (utf8.size > 0xFFFF) throw IOException("Encoded UTF-8 too long: ${utf8.size}")
        buffer.writeShort(utf8.size)
        buffer.write(utf8)
    }
}