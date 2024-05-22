package kurenai.imsyncbot.bot.qq

import com.github.nyayurn.yutori.Event
import com.github.nyayurn.yutori.MessageEvent
import com.github.nyayurn.yutori.RootActions
import com.github.nyayurn.yutori.Satori
import kurenai.imsyncbot.ConfigProperties
import kurenai.imsyncbot.bot.telegram.TelegramBot
import kurenai.imsyncbot.groupConfigRepository
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.TelegramUtil.escapeMarkdownChar

class SatoriHandler(val configProperties: ConfigProperties) {

    val tgMsgFormat = configProperties.bot.tgMsgFormat

    suspend fun <T: Event> onGroup(actions: RootActions, event: T, satori: Satori, telegramBot: TelegramBot) {
        val groupId = actions.guild.self_id?.toLongOrNull()?: return
        val config = groupConfigRepository.findByQqGroupId(groupId)?:return

        when (event) {
            is MessageEvent -> {
                    telegramBot.sendMessageText(
                        text = event.message.content.formatMsg(event.member?.user?.id?.toLongOrNull()?: -1L, event.member?.user?.nick?:event.member?.user?.name?:"Unknown"),
                        chatId = config.telegramGroupId,
                    )
            }
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
        return tgMsgFormat.escapeMarkdownChar().replace(BotUtil.NEWLINE_PATTERN.escapeMarkdownChar(), "\n", true)
            .replace(BotUtil.ID_PATTERN.escapeMarkdownChar(), senderId.toString(), true)
            .let {
                if (senderName?.isNotBlank() == true)
                    it.replace(BotUtil.NAME_PATTERN.escapeMarkdownChar(), senderName.escapeMarkdownChar(), true)
                else
                    it.replace(BotUtil.NAME_PATTERN.escapeMarkdownChar(), "", true)
            }.replace(BotUtil.MSG_PATTERN.escapeMarkdownChar(), this, true)
    }
}