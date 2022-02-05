package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractQQCommand
import kurenai.imsyncbot.config.UserConfig
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.request.message.EditMessageText
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.QuoteReply
import org.springframework.stereotype.Component

@Component
class AcceptLinkCommand : AbstractQQCommand() {

    override suspend fun execute(event: MessageEvent): Int {
        if (event.subject is Group) {
            val reply = event.message[QuoteReply.Key] ?: return 0
            val pair = reply.let { LinkCommand.holdLinks[it.source.ids[0]] } ?: return 0
            if (pair.first != event.sender.id) {
                event.subject.sendMessage(event.message.quote().plus("非绑定目标QQ"))
                return 1
            }
            if (event.message.filterIsInstance(PlainText::class.java).joinToString("") { it.content }.contains("accept")) {
                UserConfig.link(pair.first, pair.second[0].from!!.id, pair.second[0].from!!.username!!)
                LinkCommand.holdLinks.remove(reply.source.ids[0])
                event.subject.sendMessage(event.message.quote().plus("绑定成功"))
                EditMessageText("绑定成功").apply {
                    chatId = pair.second[0].chatId
                    messageId = pair.second[1].messageId
                }.send()
                return 1
            }
        }
        return 0
    }


}