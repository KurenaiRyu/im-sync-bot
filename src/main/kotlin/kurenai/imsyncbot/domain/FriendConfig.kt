package kurenai.imsyncbot.domain

import kurenai.imsyncbot.jimmer.SnowFlakeGenerator
import org.babyfish.jimmer.sql.*

@Entity
@Table(name = "FRIEND_CONFIG")
interface FriendConfig {

    @Id
    @GeneratedValue(generatorType = SnowFlakeGenerator::class)
    val id: Long
    val qqId: Long
    val name: String
    val telegramGroupId: Long

    @Version
    val version: Int

}