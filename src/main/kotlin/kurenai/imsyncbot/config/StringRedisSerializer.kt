package kurenai.imsyncbot.config

import org.springframework.data.redis.serializer.RedisSerializer

/**
 * Kryo Codec
 *
 * @author Kurenai
 * @since 2020-03-13 10:15
 */
class StringRedisSerializer : RedisSerializer<String> {

    override fun serialize(t: String?): ByteArray {
        return t?.toByteArray() ?: EMPTY
    }

    override fun deserialize(bytes: ByteArray?): String? {
        return bytes?.let { String() }
    }

    companion object {
        private val EMPTY = ByteArray(0)
    }
}