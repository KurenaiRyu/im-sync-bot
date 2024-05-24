package kurenai.imsyncbot.bot.qq

import com.github.nyayurn.yutori.MessageEvent
import com.github.nyayurn.yutori.RootActions
import com.github.nyayurn.yutori.Satori
import com.github.nyayurn.yutori.nick
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.groupConfigRepository
import kurenai.imsyncbot.utils.*

class SatoriHandler(val configProperties: ConfigProperties) {

    companion object {
        val log = getLogger()
    }

    val tgMsgFormat = configProperties.bot.tgMsgFormat

    suspend fun onMessage(actions: RootActions, event: MessageEvent, satori: Satori, telegramBot: TelegramBot) {
        val groupId = event.guild?.id?.toLongOrNull() ?: return
        val config = withContext(Dispatchers.IO) {
            groupConfigRepository.findByQqGroupId(groupId)
        } ?: return

        if (event.message.content.contains("<img")) {
            val url = event.message.content.substringAfter("src=\"").substringBefore("\"")
            telegramBot.sendMessagePhoto(
                data = httpClient.get(url).body<ByteArray>(),
                formattedText = "".formatMsg(
                    event.user.id.toLongOrNull() ?: -1L,
                    event.nick() ?: "Unknown"
                ).fmt(),
                chatId = config.telegramGroupId
            ).also {
                log.info("Sent {}", it)
            }
            return
        }

        telegramBot.sendMessageText(
            text = event.message.content.formatMsg(
                event.user.id.toLongOrNull() ?: -1L,
                event.nick() ?: "Unknown"
            ),
            chatId = config.telegramGroupId,
            parseMode = ParseMode.MARKDOWN_V2
        ).also {
            log.info("Sent {}", it)
        }

        config.telegramGroupId
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
        return tgMsgFormat.escapeMarkdown().replace(BotUtil.NEWLINE_PATTERN.escapeMarkdown(), "\n", true)
            .replace(BotUtil.ID_PATTERN.escapeMarkdown(), senderId.toString(), true)
            .let {
                if (senderName?.isNotBlank() == true)
                    it.replace(BotUtil.NAME_PATTERN.escapeMarkdown(), senderName.escapeMarkdown(), true)
                else
                    it.replace(BotUtil.NAME_PATTERN.escapeMarkdown(), "", true)
            }.replace(BotUtil.MSG_PATTERN.escapeMarkdown(), this, true)
    }
}