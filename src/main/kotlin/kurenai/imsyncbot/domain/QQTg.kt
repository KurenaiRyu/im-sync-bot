package kurenai.imsyncbot.domain

import kurenai.imsyncbot.jimmer.SnowFlakeGenerator
import org.babyfish.jimmer.sql.*


/**
 * @author Kurenai
 * @since 2023/7/22 19:40
 */
@Entity
@Table(name = "QQ_TG")
interface QQTg {
    @Id
    @GeneratedValue(generatorType = SnowFlakeGenerator::class)
    val id: Long
    val qqId: Long
    val qqMsgId: Int
    val tgGrpId: Long
    val tgMsgId: Long

    @Version
    val version: Int
}