package kurenai.imsyncbot.config

import kurenai.imsyncbot.HandlerHolder
import kurenai.imsyncbot.handler.Handler
import kurenai.imsyncbot.qq.QQBotProperties
import kurenai.imsyncbot.repository.BotConfigRepository
import kurenai.imsyncbot.telegram.ProxyProperties
import kurenai.imsyncbot.telegram.TelegramBotProperties
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
    fun botInitializer(botConfigRepository: BotConfigRepository): BotInitializer {
        return BotInitializer(botConfigRepository)
    }

}