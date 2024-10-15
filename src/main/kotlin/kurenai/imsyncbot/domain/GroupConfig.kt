package kurenai.imsyncbot.domain

import kurenai.imsyncbot.jimmer.SnowFlakeGenerator
import kurenai.imsyncbot.service.GroupStatus
import org.babyfish.jimmer.Scalar
import org.babyfish.jimmer.sql.*


/**
 * @author Kurenai
 * @since 2023/7/22 18:26
 */

@Entity
@Table(name = "GROUP_CONFIG")
interface GroupConfig {
    @Id
    @GeneratedValue(generatorType = SnowFlakeGenerator::class)
    val id: Long
    val qqGroupId: Long?
    val name: String
    val telegramGroupId: Long
    val discordChannelId: Long?
    @Scalar
    val status: Set<GroupStatus>

    @Version
    val version: Int
}