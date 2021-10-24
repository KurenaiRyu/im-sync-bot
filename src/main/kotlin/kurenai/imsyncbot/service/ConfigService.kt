package kurenai.imsyncbot.service

import kurenai.imsyncbot.BotConfigKey
import kurenai.imsyncbot.domain.BotConfig
import kurenai.imsyncbot.repository.ConfigRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class ConfigService(
    private val configRepository: ConfigRepository
) {

    private val log = KotlinLogging.logger {}

    fun get(key: BotConfigKey): String? {
        return try {
            configRepository.getById(key.value).value
        } catch (e: Exception) {
            log.error(e) { "Get config error: ${e.message}" }
            null
        }
    }

    fun exist(key: BotConfigKey): Boolean {
        return configRepository.existsById(key.value)
    }

    fun save(key: BotConfigKey, value: Any) {
        configRepository.save(BotConfig(key, value))
    }

    fun saveAll(configs: List<BotConfig>) {
        configRepository.saveAll(configs)
    }
}