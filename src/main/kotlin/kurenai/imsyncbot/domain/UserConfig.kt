package kurenai.imsyncbot.domain

import kurenai.imsyncbot.jimmer.SnowFlakeGenerator
import kurenai.imsyncbot.service.UserStatus
import org.babyfish.jimmer.Scalar
import org.babyfish.jimmer.sql.*


/**
 * @author Kurenai
 * @since 2023/7/22 18:26
 */
@Entity
@Table(name = "USER_CONFIG")
interface UserConfig {
    @Id
    @GeneratedValue(generatorType = SnowFlakeGenerator::class)
    val id: Long
    val tg: Long?
    val qq: Long?
    val bindingName: String?
    @Scalar
    val status: Set<UserStatus>

    @Version
    val version: Int
}