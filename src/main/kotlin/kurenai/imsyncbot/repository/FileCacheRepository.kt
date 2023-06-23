package kurenai.imsyncbot.repository

import kurenai.imsyncbot.domain.FileCache
import kurenai.imsyncbot.domain.QQTg
import org.springframework.data.jpa.repository.JpaRepository

/**
 * @author Kurenai
 * @since 2023/6/3 16:36
 */

interface FileCacheRepository : JpaRepository<FileCache, String> {

}