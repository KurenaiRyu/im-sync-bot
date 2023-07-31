package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.GroupConfig
import kurenai.imsyncbot.domain.UserConfig
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * @author Kurenai
 * @since 2023/6/18 21:17
 */

interface UserConfigRepository : JpaRepository<UserConfig, Long>, JpaSpecificationExecutor<UserConfig> {

}