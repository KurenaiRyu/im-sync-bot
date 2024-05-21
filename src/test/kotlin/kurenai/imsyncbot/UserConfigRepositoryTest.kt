package kurenai.imsyncbot

import jakarta.persistence.EntityGraph
import jakarta.persistence.EntityManager
import kurenai.imsyncbot.domain.UserConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserConfigRepositoryTest {

    @Autowired
    private lateinit var em: EntityManager

    @Test
    fun test() {
        val entityGraph: EntityGraph<*>? = em.getEntityGraph("user")
        val cb = em.criteriaBuilder
        val criteriaQuery = cb.createQuery()
        val root = criteriaQuery.from(UserConfig::class.java)
        val typedQuery =
            em.createQuery(criteriaQuery.where(cb.equal(root.get<String>(UserConfig.Fields.bindingName), "kurenai")))
        typedQuery.setHint("jakarta.persistence.fetchgraph", entityGraph).resultList as List<UserConfig>
    }
}