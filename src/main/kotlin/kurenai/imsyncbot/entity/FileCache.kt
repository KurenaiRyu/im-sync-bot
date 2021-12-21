package kurenai.imsyncbot.entity

class FileCache {

    lateinit var fileId: String
    var fileSize = 0L

    constructor(fileId: String, fileSize: Long) {
        this.fileId = fileId
        this.fileSize = fileSize
    }

    constructor()
}