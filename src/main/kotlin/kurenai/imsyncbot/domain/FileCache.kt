package kurenai.imsyncbot.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "FILE_CACHE")
class FileCache(
    @Id var md5: String,
    var fileId: String,
    var fileType: FileType
) {
    enum class FileType {
        IMAGE, DOCUMENT
    }
}
