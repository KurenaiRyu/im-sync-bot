package kurenai.imsyncbot.bot.qq

import com.github.nyayurn.yutori.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.groupConfigRepository
import kurenai.imsyncbot.utils.*
import org.jsoup.Jsoup

class SatoriHandler(val configProperties: ConfigProperties) {

    companion object {
        val log = getLogger()

        private val NAME_PATTERN = BotUtil.NAME_PATTERN.escapeMarkdown()
        private val ID_PATTERN = BotUtil.ID_PATTERN.escapeMarkdown()
        private val MSG_PATTERN = BotUtil.MSG_PATTERN.escapeMarkdown()
        private val NEWLINE_PATTERN = BotUtil.NEWLINE_PATTERN.escapeMarkdown()
    }

    val tgMsgFormat = configProperties.bot.tgMsgFormat

    suspend fun onMessage(actions: RootActions, event: Event<MessageEvent>, satori: Satori, telegramBot: TelegramBot) {
        val groupId = event.channel.id.toLongOrNull() ?: return
        val config = withContext(Dispatchers.IO) {
            groupConfigRepository.findByQqGroupId(groupId)
        } ?: return

        val content = event.message.content
        val body = Jsoup.parse(content).body()
        var text = body.text().escapeMarkdown()
        val imgUrl = body.getElementsByTag("img").attr("src")
        val videoUrl = body.getElementsByTag("video").attr("src")

        val at = body.getElementsByTag("at")
        at.attr("id").toLongOrNull()?.let { atId ->
            val atName = at.attr("name")
            val atTgId =
                if (atId == configProperties.bot.masterOfQq) configProperties.bot.masterOfTg else atId  //TODO: find bind user telegram id
            text = "[$atName](https://t.me/$atTgId) $text"
        }


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
        }.also {
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