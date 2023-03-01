package kurenai.imsyncbot.domain

import kotlinx.serialization.Serializable

/**
 * @author Kurenai
 * @since 9/2/2022 17:17:18
 */

@Serializable
data class QQRichMessage(
    val action: String,
    val url: String?
)