package kurenai.imsyncbot.discord

import org.springframework.context.annotation.Bean

//@Configuration
class DiscordConfig {

    @Bean
    fun discordBotInitializer(): DiscordBotInitializer {
        return DiscordBotInitializer()
    }

}