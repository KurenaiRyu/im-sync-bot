package kurenai.mybot.config

import kurenai.mybot.HandlerHolder
import kurenai.mybot.handler.Handler
import kurenai.mybot.qq.QQBotProperties
import kurenai.mybot.telegram.ProxyProperties
import kurenai.mybot.telegram.TelegramBotProperties
import kurenai.mybot.utils.SnowFlake
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.telegram.telegrambots.bots.DefaultBotOptions

/**
 * @author Kurenai
 * @since 2021-06-30 14:08
 */
@Configuration
@EnableConfigurationProperties(TelegramBotProperties::class, ProxyProperties::class, QQBotProperties::class, BotProperties::class)
class BotAutoConfiguration {
    @Bean
    fun handlerHolder(@Lazy handlerList: List<Handler>): HandlerHolder {
        return HandlerHolder(handlerList)
    }

    @Bean
    fun defaultBotOptions(proxyProperties: ProxyProperties, telegramBotProperties: TelegramBotProperties): DefaultBotOptions {
        val botOptions = DefaultBotOptions()
        telegramBotProperties.baseUrl.takeIf { it.isNotBlank() }?.let(botOptions::setBaseUrl)
        if (proxyProperties.type == DefaultBotOptions.ProxyType.NO_PROXY) return botOptions
        botOptions.proxyType = proxyProperties.type
        botOptions.proxyHost = proxyProperties.host
        botOptions.proxyPort = proxyProperties.port
        return botOptions
    }

    @Bean
    fun snowFlake(): SnowFlake {
        return SnowFlake(1, 1)
    }
}