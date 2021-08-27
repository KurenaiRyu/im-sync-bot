package kurenai.mybot.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import java.io.Serializable

@Configuration
@ConditionalOnClass(RedisConnectionFactory::class, ObjectMapper::class)
class RedisConfig {

    /**
     * 设置 redisTemplate 序列化
     */
    @Bean
    fun redisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        mapper: ObjectMapper,
    ): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.setConnectionFactory(redisConnectionFactory)
        val stringRedisSerializer = StringRedisSerializer()
        template.keySerializer = stringRedisSerializer
        template.hashKeySerializer = stringRedisSerializer
        template.setDefaultSerializer(KryoRedisSerializer())
        template.afterPropertiesSet()
        redisConnectionFactory.connection.echo("hello".toByteArray())
        return template
    }

    /**
     * json序列化
     */
    fun jackson2JsonRedisSerializer(mapper: ObjectMapper): RedisSerializer<Serializable> {
        val serializer = Jackson2JsonRedisSerializer(Serializable::class.java)
        serializer.setObjectMapper(mapper)
        return serializer
    }
}