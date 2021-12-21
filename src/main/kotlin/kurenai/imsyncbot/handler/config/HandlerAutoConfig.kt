package kurenai.imsyncbot.handler.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ForwardHandlerProperties::class)
class HandlerAutoConfig {

    @Bean
    fun forwardHandlerInitializer(
        forwardHandlerProperties: ForwardHandlerProperties,
    ): ForwardHandlerInitializer {
        return ForwardHandlerInitializer(forwardHandlerProperties)
    }

}