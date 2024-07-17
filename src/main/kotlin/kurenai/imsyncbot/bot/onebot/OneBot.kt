package kurenai.imsyncbot.bot.onebot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kurenai.imsyncbot.BotProperties
import kurenai.imsyncbot.groupConfigRepository
import moe.kurenai.cq.Bot
import moe.kurenai.cq.EventHandler
import moe.kurenai.cq.event.Event
import moe.kurenai.cq.event.group.GroupMessageEvent
import moe.kurenai.cq.model.MessageType
import org.jsoup.Jsoup
import kotlin.coroutines.CoroutineContext

class OneBot (
    val properties: BotProperties
): CoroutineScope {

    val bot = Bot.newBot {
        port = 9000
    }
    override val coroutineContext: CoroutineContext = bot.coroutineContext

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
        messageChain.filter { it.type == MessageType.TEXT }.

        val body = Jsoup.parse(messageChain).body()
        val text = body.text().escapeMarkdown()
        val imgUrl = body.getElementsByTag("img").attr("src")
        val videoUrl = body.getElementsByTag("video").attr("src")
    }

}