package com.tencent.mobileqq.dt.model

import kotlinx.serialization.Serializable
import kurenai.imsyncbot.utils.json
import java.io.File
import java.nio.charset.Charset

object FEBound {
    private const val LEVEL1 = 32
    private const val LEVEL2 = 16
    private const val Type_Decode = 2
    private const val Type_Encode = 1
    private var mConfigEnCode = arrayOf(
        byteArrayOf(15, 10, 13, 4, 0, 5, 3, 1, 11, 12, 7, 2, 14, 6, 9, 8),
        byteArrayOf(12, 1, 13, 4, 14, 9, 8, 6, 5, 3, 10, 7, 11, 2, 0, 15),
        byteArrayOf(10, 5, 14, 0, 9, 1, 7, 4, 11, 8, 3, 15, 12, 6, 13, 2),
        byteArrayOf(7, 11, 2, 14, 3, 10, 1, 8, 0, 15, 9, 6, 13, 4, 5, 12),
        byteArrayOf(5, 15, 1, 2, 4, 13, 7, 8, 3, 6, 11, 0, 9, 10, 12, 14),
        byteArrayOf(2, 5, 0, 9, 3, 15, 11, 7, 8, 13, 10, 4, 1, 14, 6, 12),
        byteArrayOf(15, 12, 6, 14, 7, 2, 0, 10, 11, 13, 3, 5, 1, 4, 9, 8),
        byteArrayOf(13, 2, 0, 1, 8, 10, 4, 14, 11, 12, 7, 3, 15, 9, 5, 6),
        byteArrayOf(10, 6, 7, 8, 9, 3, 15, 1, 2, 5, 11, 12, 13, 14, 0, 4),
        byteArrayOf(8, 10, 4, 2, 13, 15, 12, 7, 6, 3, 14, 0, 1, 5, 9, 11),
        byteArrayOf(5, 7, 12, 6, 0, 2, 14, 3, 1, 13, 9, 10, 15, 11, 8, 4),
        byteArrayOf(2, 7, 12, 1, 9, 10, 4, 8, 13, 11, 6, 3, 0, 5, 15, 14),
        byteArrayOf(0, 11, 9, 3, 12, 8, 14, 13, 5, 4, 10, 15, 7, 2, 1, 6),
        byteArrayOf(13, 1, 0, 12, 6, 14, 7, 11, 3, 10, 2, 5, 15, 8, 4, 9),
        byteArrayOf(10, 7, 5, 1, 0, 6, 9, 13, 14, 8, 3, 15, 11, 12, 4, 2),
        byteArrayOf(7, 14, 5, 13, 4, 11, 15, 10, 8, 0, 12, 2, 3, 1, 9, 6),
        byteArrayOf(5, 2, 1, 12, 10, 14, 4, 15, 9, 8, 6, 0, 13, 11, 7, 3),
        byteArrayOf(2, 8, 6, 5, 1, 3, 14, 10, 0, 12, 4, 13, 7, 15, 9, 11),
        byteArrayOf(0, 12, 3, 11, 10, 5, 4, 14, 9, 7, 1, 2, 13, 8, 6, 15),
        byteArrayOf(13, 3, 7, 11, 4, 10, 15, 0, 5, 2, 6, 12, 14, 9, 8, 1),
        byteArrayOf(11, 7, 4, 6, 3, 0, 14, 5, 2, 9, 13, 15, 10, 8, 12, 1),
        byteArrayOf(8, 13, 0, 11, 4, 1, 3, 9, 10, 15, 12, 5, 14, 7, 6, 2),
        byteArrayOf(5, 3, 11, 10, 13, 6, 1, 15, 12, 8, 2, 4, 9, 14, 0, 7),
        byteArrayOf(3, 7, 9, 6, 0, 5, 10, 14, 1, 13, 11, 4, 2, 15, 8, 12),
        byteArrayOf(0, 14, 12, 15, 11, 1, 3, 10, 8, 2, 9, 6, 13, 5, 7, 4),
        byteArrayOf(13, 4, 8, 6, 3, 7, 10, 0, 14, 5, 9, 1, 15, 12, 2, 11),
        byteArrayOf(11, 8, 13, 5, 3, 14, 6, 9, 1, 0, 12, 15, 2, 7, 10, 4),
        byteArrayOf(8, 14, 13, 10, 7, 3, 0, 6, 11, 12, 5, 1, 15, 4, 9, 2),
        byteArrayOf(6, 2, 14, 10, 15, 1, 5, 8, 9, 7, 11, 13, 4, 3, 12, 0),
        byteArrayOf(3, 9, 2, 4, 5, 8, 1, 7, 11, 10, 12, 0, 15, 13, 6, 14),
        byteArrayOf(0, 15, 6, 14, 11, 2, 1, 3, 13, 4, 10, 12, 7, 5, 8, 9),
        byteArrayOf(13, 5, 10, 7, 2, 4, 11, 0, 14, 8, 1, 9, 3, 12, 6, 15)
    )
    private var mConfigDeCode = arrayOf(
        byteArrayOf(13, 7, 12, 2, 14, 11, 3, 8, 5, 9, 10, 6, 1, 15, 0, 4),
        byteArrayOf(13, 8, 4, 1, 9, 15, 12, 2, 11, 7, 10, 0, 3, 5, 6, 14),
        byteArrayOf(7, 3, 9, 13, 2, 6, 14, 1, 5, 0, 8, 4, 11, 12, 10, 15),
        byteArrayOf(9, 5, 14, 13, 7, 10, 0, 6, 2, 15, 3, 4, 11, 1, 8, 12),
        byteArrayOf(11, 1, 4, 9, 0, 2, 6, 10, 5, 8, 14, 15, 7, 3, 12, 13),
        byteArrayOf(3, 0, 12, 10, 5, 13, 15, 1, 11, 2, 7, 4, 8, 9, 6, 14),
        byteArrayOf(1, 13, 15, 12, 10, 6, 11, 4, 5, 3, 7, 9, 14, 2, 0, 8),
        byteArrayOf(3, 8, 1, 11, 6, 4, 13, 10, 15, 7, 2, 5, 0, 14, 12, 9),
        byteArrayOf(7, 6, 13, 9, 5, 14, 3, 15, 1, 0, 10, 4, 11, 8, 2, 12),
        byteArrayOf(7, 14, 15, 5, 6, 11, 9, 0, 4, 10, 8, 2, 1, 12, 3, 13),
        byteArrayOf(8, 5, 14, 1, 0, 4, 7, 13, 6, 10, 15, 3, 12, 9, 11, 2),
        byteArrayOf(15, 0, 5, 2, 7, 3, 8, 12, 11, 1, 13, 4, 14, 6, 9, 10),
        byteArrayOf(12, 8, 10, 6, 15, 5, 14, 9, 7, 3, 13, 11, 2, 1, 4, 0),
        byteArrayOf(15, 6, 4, 1, 2, 5, 13, 11, 9, 14, 3, 12, 0, 10, 7, 8),
        byteArrayOf(6, 4, 8, 9, 3, 12, 14, 2, 10, 0, 1, 15, 11, 13, 5, 7),
        byteArrayOf(9, 13, 11, 12, 4, 2, 15, 0, 8, 14, 7, 5, 10, 3, 1, 6),
        byteArrayOf(1, 4, 8, 10, 0, 7, 15, 9, 2, 3, 14, 13, 11, 6, 5, 12),
        byteArrayOf(4, 0, 10, 3, 13, 7, 11, 9, 5, 6, 1, 14, 2, 12, 15, 8),
        byteArrayOf(3, 4, 14, 8, 5, 13, 10, 9, 6, 2, 11, 15, 12, 7, 1, 0),
        byteArrayOf(6, 10, 12, 7, 4, 1, 13, 8, 5, 3, 11, 14, 0, 2, 15, 9),
        byteArrayOf(2, 11, 14, 13, 3, 9, 8, 12, 4, 1, 0, 15, 7, 10, 5, 6),
        byteArrayOf(6, 15, 3, 4, 9, 7, 11, 0, 5, 14, 13, 10, 12, 8, 2, 1),
        byteArrayOf(4, 14, 2, 5, 0, 1, 10, 7, 3, 13, 6, 15, 12, 8, 11, 9),
        byteArrayOf(11, 13, 0, 3, 5, 14, 9, 7, 4, 1, 8, 15, 6, 12, 10, 2),
        byteArrayOf(11, 3, 1, 8, 7, 12, 6, 10, 5, 9, 15, 14, 2, 13, 4, 0),
        byteArrayOf(10, 13, 3, 14, 15, 12, 1, 2, 11, 7, 4, 6, 0, 5, 9, 8),
        byteArrayOf(1, 15, 5, 3, 2, 6, 7, 4, 8, 11, 0, 14, 12, 13, 10, 9),
        byteArrayOf(1, 10, 5, 7, 15, 14, 12, 0, 11, 8, 4, 2, 3, 6, 13, 9),
        byteArrayOf(1, 2, 12, 14, 8, 0, 6, 10, 3, 9, 7, 15, 11, 4, 13, 5),
        byteArrayOf(10, 5, 0, 14, 15, 13, 3, 11, 8, 2, 4, 1, 6, 7, 12, 9),
        byteArrayOf(8, 1, 4, 12, 11, 7, 9, 5, 14, 6, 10, 3, 13, 2, 15, 0),
        byteArrayOf(7, 10, 4, 12, 5, 1, 14, 3, 9, 11, 2, 6, 13, 0, 8, 15)
    )

    fun initAssertConfig(baseDir: File) {
        val target = File(baseDir, "dtconfig.json")
        val text = target.readText(Charset.defaultCharset())
        val dtConfig = json.decodeFromString(DtConfig.serializer(), text)
        mConfigEnCode = dtConfig.en
        mConfigDeCode = dtConfig.de
    }

    fun transform(i: Int, bArr: ByteArray): ByteArray? {
        return try {
            val bArr2 = ByteArray(bArr.size)
            val bArr3 = mConfigEnCode
            if (bArr3.size == LEVEL1 && i == Type_Encode) {
                transformInner(bArr, bArr2, bArr3)
            } else {
                val bArr4 = mConfigDeCode
                if (bArr4.size == LEVEL1 && i == Type_Decode) {
                    transformInner(bArr, bArr2, bArr4)
                } else {
                    //a.a(TAG, (int) Type_Encode, "transform error!");
                }
            }
            bArr2
        } catch (th: Throwable) {
            th.printStackTrace()
            //a.a(TAG, (int) Type_Encode, "encode error!" + th);
            null
        }
    }

    private fun transformInner(bArr: ByteArray, bArr2: ByteArray, bArr3: Array<ByteArray>) {
        var i = 0
        while (i < bArr.size) {
            val i2 = i * Type_Decode
            bArr2[i] = (bArr3[(i2 + Type_Encode) % LEVEL1][(bArr[i].toInt() and 15).toByte()
                .toInt()].toInt() or (bArr3[i2 % LEVEL1][(bArr[i].toInt() shr 4 and 15).toByte()
                .toInt()].toInt() shl 4)).toByte()
            i += Type_Encode
        }
    }
}

@Serializable
data class DtConfig(
    val en: Array<ByteArray>,
    val de: Array<ByteArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DtConfig

        if (!en.contentDeepEquals(other.en)) return false
        return de.contentDeepEquals(other.de)
    }

    override fun hashCode(): Int {
        var result = en.contentDeepHashCode()
        result = 31 * result + de.contentDeepHashCode()
        return result
    }
}
