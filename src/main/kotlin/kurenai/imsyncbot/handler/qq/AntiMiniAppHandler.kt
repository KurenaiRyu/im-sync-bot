package kurenai.imsyncbot.handler.qq

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.handler.Handler.Companion.BREAK
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.config.AntiMiniAppHandlerProperties
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import mu.KotlinLogging
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import org.apache.commons.lang3.StringUtils
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

@Component
@EnableConfigurationProperties(
    AntiMiniAppHandlerProperties::class, ForwardHandlerProperties::class
)
class AntiMiniAppHandler(private val properties: AntiMiniAppHandlerProperties) : QQHandler {

    private val log = KotlinLogging.logger {}
    private val jsonMapper = ObjectMapper()
    private val xmlMapper = XmlMapper()

    @Throws(Exception::class)
    override suspend fun onGroupMessage(event: GroupAwareMessageEvent): Int {
        val id = event.subject.id
        val chatId = ContextHolder.qqTgBinding.getOrDefault(id, ContextHolder.defaultTgGroup)
        val ignoreGroup = properties.ignoreGroup
        if (ignoreGroup.isNotEmpty() && ignoreGroup.contains(id)) return CONTINUE
        val content = event.message.contentToString()
        val title: String
        val url: String
        if (content.startsWith("<?xml version='1.0'")) {
            log.debug("starting parse xml: $content")
            try {
                val jsonNode = xmlMapper.readTree(content)
                val action = jsonNode["action"].asText("")
                if (action == "web") {
                    title = (jsonNode["title"] ?: jsonNode["item"]?.get("summary"))?.asText() ?: ""
                    url = handleUrl(jsonNode["url"]?.asText() ?: "")
                    if (properties.enable) event.subject.sendMessage("title: $title\nurl: $url")
                    sendTg(chatId.toString(), url)
                    return BREAK
                }
            } catch (e: JsonProcessingException) {
                log.error(e.message, e)
            }
        } else if (content.contains("\"app\":")) {
            log.debug("starting parse json: $content")
            try {
                val jsonNode = jsonMapper.readTree(content)
                if (jsonNode["view"].asText("") == "news") {
                    val news = jsonNode["meta"]?.get("news")
                    title = news?.get("title")?.asText() ?: ""
                    url = news?.get("jumpUrl")?.asText() ?: ""
                } else {
                    val item = jsonNode["meta"]?.get("detail_1")
                    title = item?.get("desc")?.asText() ?: ""
                    url = item?.get("qqdocurl")?.asText() ?: ""
                }
                handleUrl(url)
                if (properties.enable) event.subject.sendMessage("title: $title\nurl: $url")
                sendTg(chatId.toString(), url)
            } catch (e: JsonProcessingException) {
                log.error(e.message, e)
            }
        }
        return CONTINUE
    }

    override fun order(): Int {
        return 50
    }

    private fun sendTg(chatId: String, url: String) {
        try {
            ContextHolder.telegramBotClient.execute(
                SendMessage.builder()
                    .chatId(chatId)
                    .text(url.takeIf { it.isNotEmpty() } ?: " ")
                    .build()
            )
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun handleUrl(url: String): String {
        if (StringUtils.isNotBlank(url) && url.contains("?") && url.contains("b23.tv")) {
            // ok http get real url
            return url.substring(0, url.indexOf("?"))
        }
        return url
    }
}