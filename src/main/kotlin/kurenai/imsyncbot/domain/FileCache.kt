package kurenai.imsyncbot.domain

import org.babyfish.jimmer.sql.*

/**
 * @author Kurenai
 * @since 2023/7/22 19:30
 */
@Entity
@Table(name = "FILE_CACHE")
interface FileCache {
    @Id
    val id: String
    val fileId: String
    val fileType: String

    @Version
    val version: Int
}