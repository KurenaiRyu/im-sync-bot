package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.BindingGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BindingGroupRepository : JpaRepository<BindingGroup, Long> {

    fun deleteByQq(qq: Long)

    fun deleteByTg(tg: Long)
}