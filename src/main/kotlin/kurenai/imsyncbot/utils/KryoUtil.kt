package kurenai.imsyncbot.utils

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.Pool
import org.apache.commons.codec.binary.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets

/**
 * Kryo Utils
 *
 *
 */
object KryoUtil {
    private val DEFAULT_ENCODING = StandardCharsets.UTF_8
    private val KRYO_POOL: Pool<Kryo> = object : Pool<Kryo>(true, false, 8) {
        override fun create(): Kryo {
            val kryo = Kryo()
            // Configure the Kryo instance.
            kryo.isRegistrationRequired = false
            kryo.references = true
            return kryo
        }
    }

    /**
     * 获得一个 Kryo 实例
     *
     * @return 当前线程的 Kryo 实例
     */
    val instance: Kryo
        get() = KRYO_POOL.obtain()
    //-----------------------------------------------
    //          序列化/反序列化对象，及类型信息
    //          序列化的结果里，包含类型的信息
    //          反序列化时不再需要提供类型
    //-----------------------------------------------
    /**
     * 将对象【及类型】序列化为字节数组
     *
     * @param obj 任意对象
     * @param <T> 对象的类型
     * @return 序列化后的字节数组
    </T> */
    fun <T> writeToByteArray(obj: T?): ByteArray? {
        if (obj == null) {
            return null
        }
        val kryo = instance
        try {
            ByteArrayOutputStream().use { byteArrayOutputStream ->
                Output(byteArrayOutputStream).use { output ->
                    kryo.writeClassAndObject(output, obj)
                    output.flush()
                    return byteArrayOutputStream.toByteArray()
                }
            }
        } catch (e: IOException) {
            throw KryoException(e.message, e.cause)
        } finally {
            KRYO_POOL.free(kryo)
        }
    }

    /**
     * 将对象【及类型】序列化为 String 利用了 Base64 编码
     *
     * @param obj 任意对象
     * @param <T> 对象的类型
     * @return 序列化后的字符串
    </T> */
    fun <T> writeToString(obj: T?): String? {
        return try {
            if (obj == null) {
                null
            } else String(Base64.encodeBase64(writeToByteArray<T>(obj)), DEFAULT_ENCODING)
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException(e)
        }
    }

    /**
     * 将字节数组反序列化为原对象
     *
     * @param byteArray writeToByteArray 方法序列化后的字节数组
     * @param <T>       原对象的类型
     * @return 原对象
    </T> */
    fun <T> readFromByteArray(byteArray: ByteArray?): T? {
        if (byteArray == null) {
            return null
        }
        val kryo = instance
        try {
            ByteArrayInputStream(byteArray).use { byteArrayInputStream ->
                Input(byteArrayInputStream).use { input ->
                    return kryo.readClassAndObject(input)?.let { it as T }
                }
            }
        } catch (e: IOException) {
            throw KryoException(e.message, e.cause)
        } finally {
            KRYO_POOL.free(kryo)
        }
    }

    /**
     * 将 String 反序列化为原对象 利用了 Base64 编码
     *
     * @param str writeToString 方法序列化后的字符串
     * @param <T> 原对象的类型
     * @return 原对象
    </T> */
    fun <T> readFromString(str: String?): T? {
        return try {
            if (str == null) {
                null
            } else readFromByteArray<T>(Base64.decodeBase64(str.toByteArray(DEFAULT_ENCODING)))
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException(e)
        }
    }
    //-----------------------------------------------
    //          只序列化/反序列化对象
    //          序列化的结果里，不包含类型的信息
    //-----------------------------------------------
    /**
     * 将对象序列化为字节数组
     *
     * @param obj 任意对象
     * @param <T> 对象的类型
     * @return 序列化后的字节数组
    </T> */
    fun <T> writeObjectToByteArray(obj: T?): ByteArray? {
        if (obj == null) {
            return null
        }
        val kryo = instance
        try {
            ByteArrayOutputStream().use { byteArrayOutputStream ->
                Output(byteArrayOutputStream).use { output ->
                    kryo.writeObject(output, obj)
                    output.flush()
                    return byteArrayOutputStream.toByteArray()
                }
            }
        } catch (e: IOException) {
            throw KryoException(e.message, e.cause)
        } finally {
            KRYO_POOL.free(kryo)
        }
    }

    /**
     * 将对象序列化为 String 利用了 Base64 编码
     *
     * @param obj 任意对象
     * @param <T> 对象的类型
     * @return 序列化后的字符串
    </T> */
    fun <T> writeObjectToString(obj: T?): String? {
        return try {
            if (obj == null) {
                null
            } else String(Base64.encodeBase64(writeObjectToByteArray<T>(obj)), DEFAULT_ENCODING)
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException(e)
        }
    }

    /**
     * 将字节数组反序列化为原对象
     *
     * @param byteArray writeToByteArray 方法序列化后的字节数组
     * @param clazz     原对象的 Class
     * @param <T>       原对象的类型
     * @return 原对象
    </T> */
    fun <T> readObjectFromByteArray(byteArray: ByteArray?, clazz: Class<T>?): T? {
        if (byteArray == null) {
            return null
        }
        val kryo = instance
        try {
            ByteArrayInputStream(byteArray).use { byteArrayInputStream -> Input(byteArrayInputStream).use { input -> return kryo.readObject(input, clazz) } }
        } catch (e: IOException) {
            throw KryoException(e.message, e.cause)
        } finally {
            KRYO_POOL.free(kryo)
        }
    }

    /**
     * 将 String 反序列化为原对象 利用了 Base64 编码
     *
     * @param str   writeToString 方法序列化后的字符串
     * @param clazz 原对象的 Class
     * @param <T>   原对象的类型
     * @return 原对象
    </T> */
    fun <T> readObjectFromString(str: String?, clazz: Class<T>?): T? {
        return try {
            if (str == null) {
                null
            } else readObjectFromByteArray(Base64.decodeBase64(str.toByteArray(DEFAULT_ENCODING)), clazz)
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException(e)
        }
    }
}