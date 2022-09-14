package kurenai.imsyncbot.domain

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author Kurenai
 * @since 9/2/2022 17:17:18
 */

data class QQAppMessage(
    val view: String,
    val meta: Meta
) {

    data class Meta(
        val news: News? = null,
        @JsonProperty("detail_1")
        val detail1: Detail? = null,
    )

    data class News(
        val jumpUrl: String?
    )

    data class Detail(
        @JsonProperty("qqdocurl")
        val qqdocurl: String?
    )
}