package kurenai.mybot.handler.impl

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kurenai.mybot.handler.Handler
import kurenai.mybot.handler.config.AntiMiniAppHandlerProperties
import kurenai.mybot.handler.config.ForwardHandlerProperties
import kurenai.mybot.qq.QQBotClient
import kurenai.mybot.telegram.TelegramBotClient
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
class AntiMiniAppHandler(private val properties: AntiMiniAppHandlerProperties, private val forwardProperties: ForwardHandlerProperties) : Handler {

    private val log = KotlinLogging.logger {}
    private val jsonMapper = ObjectMapper()
    private val xmlMapper = XmlMapper()

    @Throws(Exception::class)
    override suspend fun handleQQGroupMessage(client: QQBotClient, telegramBotClient: TelegramBotClient, event: GroupAwareMessageEvent): Boolean {
        val id = event.subject.id
        val chatId = forwardProperties.group.qqTelegram.getOrDefault(id, forwardProperties.group.defaultTelegram)
        val ignoreGroup = properties.ignoreGroup
        if (ignoreGroup.isNotEmpty() && ignoreGroup.contains(id)) return true
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
                    sendTg(telegramBotClient, chatId.toString(), url)
                    return false
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
                sendTg(telegramBotClient, chatId.toString(), url)
            } catch (e: JsonProcessingException) {
                log.error(e.message, e)
            }
        }
        return true
    }

    override fun order(): Int {
        return 50
    }

    private fun sendTg(client: TelegramBotClient, chatId: String, url: String) {
        try {
            client.execute(
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