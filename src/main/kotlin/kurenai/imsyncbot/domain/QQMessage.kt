package kurenai.imsyncbot.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import kurenai.imsyncbot.snowFlake
import net.mamoe.mirai.message.data.MessageSource
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * @author Kurenai
 * @since 2023/6/3 16:38
 */

enum class QQMessageType {
    GROUP, GROUP_TEMP, FRIEND
}

fun MessageSource.getLocalDateTime(): LocalDateTime =
    LocalDateTime.ofEpochSecond(this.time.toLong(), 0, ZoneOffset.ofHours(8))


