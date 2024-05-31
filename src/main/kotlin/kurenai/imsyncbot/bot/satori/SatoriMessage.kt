package kurenai.imsyncbot.bot.satori

class SatoriMessageChain {



}

sealed interface SatoriMessage

data class Text(
    val content: String
): SatoriMessage

data class Img(
    val url: String,
    val height: Number? = null,
    val width: Number? = null,
): SatoriMessage

data class Video(
    val url: String,
): SatoriMessage

data class At(
    val id: String? = null,
    val name: String? = null,
    val role: String? = null,
    val type: String? = null,
)