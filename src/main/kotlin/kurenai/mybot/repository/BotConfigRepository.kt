package kurenai.mybot.repository

import kurenai.mybot.domain.BotConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BotConfigRepository : JpaRepository<BotConfig, Long> {

    fun getByKey(key: String): BotConfig

}