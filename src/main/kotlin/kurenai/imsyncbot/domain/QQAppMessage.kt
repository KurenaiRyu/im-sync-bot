package kurenai.imsyncbot.domain

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable

/**
 * @author Kurenai
 * @since 9/2/2022 17:17:18
 */

@Serializable
data class QQAppMessage(
    val view: String,
    val meta: Meta
) {

    @Serializable
    data class Meta(
        val news: News? = null,
        @JsonProperty("detail_1")
        val detail1: Detail? = null,
    )

    @Serializable
    data class News(
        val jumpUrl: String?
    )

    @Serializable
    data class Detail(
        @JsonProperty("qqdocurl")
        val qqdocurl: String?
    )
}