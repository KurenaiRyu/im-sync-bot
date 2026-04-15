package kurenai.imsyncbot.command.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.ChatTypePrivate
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.utils.telegram.escapeMarkdown
import kurenai.imsyncbot.utils.telegram.fmt
import kurenai.imsyncbot.utils.telegram.messageText

@ContributesIntoSet(AppScope::class)
class HelpCommand : AbstractTelegramCommand() {

    override val command = "help"
    override val help: String = "打印该信息"
    override val onlyAdmin = false
    override val onlySupperAdmin = true

    context(bot: ImSyncBot)
    override suspend fun execute(
        message: TdApi.Message,
        sender: TdApi.MessageSenderUser,
        input: String
    ): String? {

        val chat = bot.tg.send(params = TdApi.GetChat(message.chatId))
        val filteredCommands = if (chat.type.constructor == ChatTypePrivate.CONSTRUCTOR) {
            bot.commandDispatcher.commands.values.filter { !(it.onlyGroupMessage) }
        } else {
            bot.commandDispatcher.commands.values.filter { !(it.onlyUserMessage) }
        }

        val msg = filteredCommands.joinToString("\n") {
            "`${it.command}`: _${it.help.escapeMarkdown()}_"
        }

        bot.tg.send(params = messageText(msg.fmt(), message.chatId))

        return null
    }


}