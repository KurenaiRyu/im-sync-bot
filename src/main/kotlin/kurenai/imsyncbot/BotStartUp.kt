package kurenai.imsyncbot

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.quarkus.runtime.Startup
import kurenai.imsyncbot.ContextHolder.config
import kurenai.imsyncbot.config.BotInitializer
import kurenai.imsyncbot.qq.QQBotClient
import kurenai.imsyncbot.telegram.TelegramBot
import java.io.File
import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped

@Startup
@ApplicationScoped
class BotStartUp(
    val qqclient: QQBotClient,
    val telegramBot: TelegramBot,
) {

    @PostConstruct
    fun start() {
        loadProperties()
        BotInitializer.doInit()
        qqclient.start()
        telegramBot.start()
    }

    fun loadProperties() {
        val mapper = ObjectMapper(YAMLFactory()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
        config = mapper.readValue(File("./config/config.yaml"), ConfigProperties::class.java)
    }

}