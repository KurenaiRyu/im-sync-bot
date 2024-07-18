package kurenai.imsyncbot.bot.onebot

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.BotProperties
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.bot.satori.SatoriHandler.Companion.log
import kurenai.imsyncbot.groupConfigRepository
import kurenai.imsyncbot.utils.*
import moe.kurenai.cq.Bot
import moe.kurenai.cq.EventHandler
import moe.kurenai.cq.event.Event
import moe.kurenai.cq.event.group.GroupMessageEvent
import moe.kurenai.cq.model.MessageType
import kotlin.coroutines.CoroutineContext

class OneBot (
    val imSyncBot: ImSyncBot,
    val properties: BotProperties
): CoroutineScope {

    val bot = Bot.newBot {
        port = 9000
    }
    override val coroutineContext: CoroutineContext = bot.coroutineContext

    private val tgMsgFormat = properties.tgMsgFormat

    companion object {
        private val NAME_PATTERN = BotUtil.NAME_PATTERN.escapeMarkdown()
        private val ID_PATTERN = BotUtil.ID_PATTERN.escapeMarkdown()
        private val MSG_PATTERN = BotUtil.MSG_PATTERN.escapeMarkdown()
        private val NEWLINE_PATTERN = BotUtil.NEWLINE_PATTERN.escapeMarkdown()
    }

    suspend fun start() {
        bot.addHandler(EventHandler(this::handleMessage))

        bot.start()
    }

    suspend fun handleMessage(event: Event) {

        when (event) {
            is GroupMessageEvent -> {
                handleGroupMessage(event)
            }
            else -> {}
        }
    }

    suspend fun handleGroupMessage(event: GroupMessageEvent) {

        val groupId = event.groupId
        val config = withContext(Dispatchers.IO) {
            groupConfigRepository.findByQqGroupId(groupId)
        } ?: return

        val messageChain = event.message
        val text = messageChain.filter { it.type == MessageType.TEXT }
            .mapNotNull { it.data.text }
            .fold(StringBuilder()) { acc, text -> acc.append(text) }
            .toString().escapeMarkdown()

        val imageUrls = messageChain.filter { it.type == MessageType.IMAGE }
            .mapNotNull { it.data.url }
            .fold(mutableListOf<String>()) { acc, url -> acc.also { it.add(url) } }

        val videoUrl = messageChain.filter { it.type == MessageType.VIDEO }
            .mapNotNull { it.data.url }
            .fold(mutableListOf<String>()) { acc, url -> acc.also { it.add(url) } }

        val telegramBot = imSyncBot.tg

        kotlin.runCatching {
            if (imageUrls.isNotEmpty()) {
                telegramBot.sendMessagePhoto(
                    data = httpClient.get(imageUrls[0]).body<ByteArray>(),
                    formattedText = text.formatMsg(
                        event.userId,
                        event.sender.nickname.takeIf { it.isNotBlank() }?: "Unknown"
                    ).fmt(),
                    chatId = config.telegramGroupId
                )
            } else if (videoUrl.isNotEmpty()) {
                telegramBot.sendMessageVideo(
                    data = httpClient.get(videoUrl[0]).body<ByteArray>(),
                    formattedText = text.formatMsg(
                        event.userId,
                        event.sender.nickname.takeIf { it.isNotBlank() }?: "Unknown"
                    ).fmt(),
                    chatId = config.telegramGroupId
                )
            } else {
                telegramBot.sendMessageText(
                    text = text.formatMsg(
                        event.userId,
                        event.sender.nickname.takeIf { it.isNotBlank() }?: "Unknown"
                    ),
                    chatId = config.telegramGroupId,
                    parseMode = ParseMode.MARKDOWN_V2
                )
            }
        }.recoverCatching {
            telegramBot.sendMessageText(
                text = text.formatMsg(
                    event.userId,
                    event.sender.nickname.takeIf { it.isNotBlank() }?: "Unknown"
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