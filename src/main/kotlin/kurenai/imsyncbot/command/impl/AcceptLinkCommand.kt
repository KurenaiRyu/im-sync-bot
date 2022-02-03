package kurenai.imsyncbot.command.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.imsyncbot.command.AbstractQQCommand
import kurenai.imsyncbot.config.UserConfig
import moe.kurenai.tdlight.request.message.SendMessage
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply
import org.springframework.stereotype.Component

@Component
class AcceptLinkCommand : AbstractQQCommand() {

    override fun execute(event: MessageEvent) {
        CoroutineScope(Dispatchers.Default).launch {
            val group = event.subject as Group
            event.message[QuoteReply.Key]?.let { LinkCommand.holdLinks[it.source.ids[0]] }?.takeIf { it.first == event.sender.id }?.let { p ->
                if (event.message.filterIsInstance(PlainText::class.java).joinToString("") { it.content } == "accept") {
                    UserConfig.link(p.first, p.second.from!!.id, p.second.from!!.username!!)
                    group.sendMessage(event.message.quote().plus("绑定成功"))
                    SendMessage(p.second.chatId, "绑定成功").apply {
                        replyToMessageId = p.second.messageId
                    }
                }
            }
        }
    }


}