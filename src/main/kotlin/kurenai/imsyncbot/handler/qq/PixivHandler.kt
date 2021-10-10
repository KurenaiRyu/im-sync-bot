package kurenai.imsyncbot.handler.qq

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import mu.KotlinLogging
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.regex.Pattern

@Component
@EnableConfigurationProperties(ForwardHandlerProperties::class)
@ConditionalOnProperty(prefix = "bot.handler.pixiv", name = ["enable"], havingValue = "true")
class PixivHandler(private val forwardProperties: ForwardHandlerProperties) : QQHandler {

    private val log = KotlinLogging.logger {}

    var pattern: Pattern = Pattern.compile("^pixiv \\d+")

    @Throws(Exception::class)
    override suspend fun onGroupMessage(event: GroupAwareMessageEvent): Int {
        val telegramBotClient = ContextHolder.telegramBotClient
        val content = event.message.serializeToMiraiCode()
        val matcher = pattern.matcher(content)
        if (matcher.find()) {
            val chartId = forwardProperties.group.qqTelegram.getOrDefault(event.subject.id, ContextHolder.defaultTgGroup).toString()
            val id = content.substring(6)
            val artUrl = PIXIV_ART_PREFIX + id
            val userUrl = PIXIV_USER_PREFIX + id
            event.subject.sendMessage(artUrl)
            event.subject.sendMessage(userUrl)
            try {
                telegramBotClient.send(SendMessage.builder().chatId(chartId).text(artUrl).build())
                telegramBotClient.send(SendMessage.builder().chatId(chartId).text(userUrl).build())
            } catch (e: TelegramApiException) {
                log.error(e.message, e)
            }
            return END
        }
        return CONTINUE
    }

    override fun order(): Int {
        return 50
    }

    companion object {
        private const val PIXIV_ART_PREFIX = "https://www.pixiv.net/artworks/"
        private const val PIXIV_USER_PREFIX = "https://www.pixiv.net/users/"
    }
}