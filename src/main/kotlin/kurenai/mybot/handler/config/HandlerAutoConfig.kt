package kurenai.mybot.handler.config

import kurenai.mybot.repository.BindingGroupRepository
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ForwardHandlerProperties::class)
class HandlerAutoConfig {

    @Bean
    fun forwardHandlerInitializer(
        forwardHandlerProperties: ForwardHandlerProperties,
        groupBindingGroupRepository: BindingGroupRepository
    ): ForwardHandlerInitializer {
        return ForwardHandlerInitializer(forwardHandlerProperties, groupBindingGroupRepository)
    }

}