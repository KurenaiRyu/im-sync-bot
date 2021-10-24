package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.BotConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ConfigRepository : JpaRepository<BotConfig, String> {

}