package kurenai.imsyncbot.utils

import java.nio.file.Path
import kotlin.io.path.inputStream

object ImageUtil {

    private val magicNumbers = arrayOf(
        ImageType.GIF to "47494638",
        ImageType.PNG to "89504e47",
        ImageType.JPEG to "ffd8ffe0",
    )

    fun determineImageType(path: Path): ImageType {
        val magicNum = readMagicNum(path).toHex()
        if (magicNum.isBlank()) return ImageType.UNKNOWN
        return magicNumbers.find { (_, num) -> num == magicNum }?.first ?: ImageType.UNKNOWN
    }
//
//    fun isGIF(path: Path) {
//        return path.inputStream().use {
//            val buff = ByteArray(4)
//            it.read(buff,0,buff.size)
//            GIF_MAGIC.contentEquals(buff)
//        }
//    }

    private fun readMagicNum(path: Path): ByteArray {
        return path.inputStream().use {
            val buff = ByteArray(4)
            it.read(buff,0,buff.size)
            buff
        }
    }

    enum class ImageType (
        final val magicNum: String,
        final val ext: String
    ) {
        GIF("47494638", "gif"),
        PNG("89504e47", "png"),
        JPEG("ffd8ffe0", "jpg"),
        UNKNOWN("", "");
    }

}