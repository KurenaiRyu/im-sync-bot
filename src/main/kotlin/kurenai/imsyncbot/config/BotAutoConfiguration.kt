package kurenai.imsyncbot.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.kurenairyu.cache.Cache
import io.github.kurenairyu.cache.redis.lettuce.LettuceCache
import io.github.kurenairyu.cache.redis.lettuce.jackson.JacksonCodec
import io.github.kurenairyu.cache.redis.lettuce.jackson.RecordNamingStrategyPatchModule
import io.lettuce.core.RedisURI
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.qq.QQBotProperties
import kurenai.imsyncbot.telegram.ProxyProperties
import kurenai.imsyncbot.telegram.TelegramBotProperties
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.codec.JsonJacksonCodec
import org.redisson.config.Config
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

/**
 * @author Kurenai
 * @since 2021-06-30 14:08
 */
@Configuration
@EnableConfigurationProperties(TelegramBotProperties::class, ProxyProperties::class, QQBotProperties::class, BotProperties::class, RedisProperties::class)
class BotAutoConfiguration {
    @Bean
    fun handlerHolder(@Lazy handlerList: List<Handler>): HandlerHolder {
        return HandlerHolder(handlerList)
    }

    @Bean
    fun cache(properties: RedisProperties, mapper: ObjectMapper): Cache {
        val redisURI = RedisURI.builder()
            .withHost(properties.host)
            .withPort(properties.port)
            .withDatabase(properties.database)
            .also { b -> properties.password?.takeIf { it.isNotBlank() }?.let { b.withPassword(it) } }
            .build()
        return LettuceCache(
            redisURI, JacksonCodec<Any>(mapper)
        )
    }

    @Bean
    fun redisson(properties: RedisProperties, mapper: ObjectMapper): RedissonClient {
        val config = Config()
        config.codec = JsonJacksonCodec(mapper)
        config.useSingleServer()
            .setAddress("redis://${properties.host}:${properties.port}")
            .setDatabase(properties.database)
            .also { c -> properties.password?.takeIf { it.isNotBlank() }?.let { c.password = it } }
        return Redisson.create(config)
    }

    @Bean
    fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper()
            .registerModules(Jdk8Module(), JavaTimeModule(), RecordNamingStrategyPatchModule())
            .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
            .activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Any::class.java).build(),
                ObjectMapper.DefaultTyping.EVERYTHING
            )
    }

}