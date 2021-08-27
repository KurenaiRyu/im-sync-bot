package kurenai.mybot.config

import kurenai.mybot.utils.KryoUtil
import org.springframework.data.redis.serializer.RedisSerializer

/**
 * Kryo Codec
 *
 * @author Kurenai
 * @since 2020-03-13 10:15
 */
class KryoRedisSerializer : RedisSerializer<Any> {

    override fun serialize(t: Any?): ByteArray? {
        return KryoUtil.writeToByteArray(t)
    }

    override fun deserialize(bytes: ByteArray?): Any? {
        return KryoUtil.readFromByteArray(bytes)
    }
}