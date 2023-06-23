package kurenai.imsyncbot.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kurenai.imsyncbot.snowFlake

@Entity
@Table(name = "FILE_CACHE")
class FileCache(
    @Id var id: String,
    var fileId: String
)
