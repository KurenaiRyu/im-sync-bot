//package kurenai.imsyncbot.bot.satori
//
//import com.github.nyayurn.yutori.*
//import io.ktor.client.call.*
//import io.ktor.client.request.*
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import kurenai.imsyncbot.ConfigProperties
//import kurenai.imsyncbot.bot.telegram.TelegramBot
//import kurenai.imsyncbot.groupConfigRepository
//import kurenai.imsyncbot.utils.*
//import org.jsoup.Jsoup
//
//        at.attr("id").toLongOrNull()?.let { atId ->
//            val atName = at.attr("name")
//            val atTgId = if (atId == configProperties.bot.masterOfQq) configProperties.bot.masterOfTg else atId  //TODO: find bind user telegram id
//            text = "[$atName](tg://user?id=$atTgId) $text"
//        }


        kotlin.runCatching {
            if (imgUrl.isNotEmpty()) {
                telegramBot.sendMessagePhoto(
                    data = httpClient.get(imgUrl).body<ByteArray>(),
                    formattedText = text.formatMsg(
                        event.user.id.toLongOrNull() ?: -1L,
                        event.nick() ?: "Unknown"
                    ).fmt(),
                    chatId = config.telegramGroupId
                )
            } else if (videoUrl.isNotEmpty()) {
                telegramBot.sendMessageVideo(
                    data = httpClient.get(videoUrl).body<ByteArray>(),
                    formattedText = text.formatMsg(
                        event.user.id.toLongOrNull() ?: -1L,
                        event.nick() ?: "Unknown"
                    ).fmt(),
                    chatId = config.telegramGroupId
                )
            } else {
                telegramBot.sendMessageText(
                    text = event.message.content.escapeMarkdown().formatMsg(
                        event.user.id.toLongOrNull() ?: -1L,
                        event.nick() ?: "Unknown"
                    ),
                    chatId = config.telegramGroupId,
                    parseMode = ParseMode.MARKDOWN_V2
                )
            }
        }.recoverCatching {
            telegramBot.sendMessageText(
                text = event.message.content.escapeMarkdown().formatMsg(
                    event.user.id.toLongOrNull() ?: -1L,
                    event.nick() ?: "Unknown"
                ),
                chatId = config.telegramGroupId,
                parseMode = ParseMode.MARKDOWN_V2
            )
        }.getOrThrow().also {
            log.info("Sent {}", it)
        }
    }

    /**
     * 格式化消息
     *
     * 字符串应经过markdown格式化，返回值已markdown格式化
     *
     * @param senderId
     * @param senderName
     * @return
     */
    private fun String.formatMsg(senderId: Long, senderName: String? = null): String {
        return tgMsgFormat.escapeMarkdown().replace(NEWLINE_PATTERN, "\n", true)
            .replace(ID_PATTERN, senderId.toString(), true)
            .let {
                if (senderName?.isNotBlank() == true)
                    it.replace(NAME_PATTERN, "`${senderName.escapeMarkdown()}`", true)
                else
                    it.replace(NAME_PATTERN, "", true)
            }.replace(MSG_PATTERN, this, true)
    }
}