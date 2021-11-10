package kurenai.imsyncbot.service

import kurenai.imsyncbot.domain.BotConfig
import kurenai.imsyncbot.repository.ConfigRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class ConfigService(
    private val configRepository: ConfigRepository
) {

    private val log = KotlinLogging.logger {}

    fun get(key: String): String? {
        return try {
            configRepository.getById(key).value
        } catch (e: Exception) {
            log.error(e) { "Get config error: ${e.message}" }
            null
        }
    }

    fun exist(key: String): Boolean {
        return configRepository.existsById(key)
    }

    fun save(key: String, value: Any) {
        configRepository.save(BotConfig(key, value))
    }

    fun saveAll(configs: List<BotConfig>) {
        configRepository.saveAll(configs)
    }

    fun findAll(): MutableList<BotConfig> {
        return configRepository.findAll()
    }

    fun delete(key: String) {
        configRepository.deleteById(key)
    }
}