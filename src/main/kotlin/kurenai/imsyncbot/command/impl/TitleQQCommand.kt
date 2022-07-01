package kurenai.imsyncbot.command.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kurenai.imsyncbot.command.AbstractQQCommand
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.PermissionDeniedException
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.MessageEvent

class TitleQQCommand : AbstractQQCommand() {

    override suspend fun execute(event: MessageEvent): Int {
        val text = event.message.contentToString()
        return if (text.startsWith("头衔")) {
            val modifyTitle = text.substring("头衔".length).trim()
            val group = event.subject as Group
            modifyTitle(group, event, modifyTitle)
            1
        } else {
            0
        }
    }

    private suspend fun modifyTitle(group: Group, event: MessageEvent, modifyTitle: String) {
        CoroutineScope(Dispatchers.Default).launch {
            group.getMember(event.sender.id)?.let {
                try {
                    it.specialTitle = modifyTitle
                    event.subject.sendMessage("[${it.nameCardOrNick}]头衔已修改为[$modifyTitle]")
                } catch (e: PermissionDeniedException) {
                    event.subject.sendMessage("bot无权修改头衔")
                }
            } ?: kotlin.run {
                event.subject.sendMessage("找不到用户[${event.sender.id}]")
            }
        }
    }
}