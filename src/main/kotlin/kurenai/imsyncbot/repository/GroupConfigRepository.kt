package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.GroupConfig
import org.springframework.data.jpa.repository.JpaRepository

/**
 * @author Kurenai
 * @since 2023/6/18 21:17
 */

interface GroupConfigRepository : JpaRepository<GroupConfig, Long> {

    fun findByQqGroupId(groupId: Long): GroupConfig?

}