package kurenai.imsyncbot.config

import io.github.kurenairyu.cache.Cache
import io.github.kurenairyu.cache.CacheFactory
import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.qq.QQBotProperties
import kurenai.imsyncbot.telegram.ProxyProperties
import kurenai.imsyncbot.telegram.TelegramBotProperties
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
    fun cache(properties: RedisProperties): Cache {
        return CacheFactory.create(properties.host, properties.port)
    }

}