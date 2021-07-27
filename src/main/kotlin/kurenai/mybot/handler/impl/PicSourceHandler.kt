package kurenai.mybot.handler.impl

import kurenai.mybot.CacheHolder
import kurenai.mybot.QQBotClient
import kurenai.mybot.TelegramBotClient
import kurenai.mybot.handler.Handler
import kurenai.mybot.handler.config.ForwardHandlerProperties
import kurenai.mybot.utils.HttpUtil
import kurenai.mybot.utils.MarkdownUtil
import kurenai.mybot.utils.MarkdownUtil.format2Markdown
import mu.KotlinLogging
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.QuoteReply
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Component
@EnableConfigurationProperties(ForwardHandlerProperties::class)
@ConditionalOnProperty(prefix = "bot.handler.pic-source", name = ["enable"], havingValue = "true")
class PicSourceHandler(private val forwardProperties: ForwardHandlerProperties) : Handler {

    private val log = KotlinLogging.logger {}
    private val cache = kurenai.mybot.cache.Cache<Long, Int>("pic-source")

    @Throws(Exception::class)
    override suspend fun handleQQGroupMessage(client: QQBotClient, telegramBotClient: TelegramBotClient, event: GroupAwareMessageEvent): Boolean {
        val message = event.message
        val image = when {
            message.contains(Image.Key) -> {
                message[Image.Key]!!
            }
            message.contains(QuoteReply.Key) -> {
                val quoteReply = message[QuoteReply.Key]!!
                quoteReply.source.ids[0].let(CacheHolder.QQ_MSG_CACHE::get)?.originalMessage?.get(Image.Key)
            }
            else -> null
        } ?: return true
        val content = message.contentToString()
        val url = image.queryUrl()
        val encodeUrl = URLEncoder.encode(url, StandardCharsets.UTF_8)
        val sauce_nao = String.format(SAUCE_NAO, encodeUrl)
        val asscii2d = String.format(ASCII2D, encodeUrl)
        when {
            content.contains("source", true) -> {
                if (overMaxQueryTimes(event)) return false
                val builder = MessageChainBuilder()
                builder.add(message.quote())
                builder.add(sauce_nao)
                event.subject.sendMessage(builder.build())
            }
            content.contains("url", true) -> {
                if (overMaxQueryTimes(event)) return false
                val builder = MessageChainBuilder()
                builder.add(message.quote())
                builder.add(url)
                event.subject.sendMessage(builder.build())
            }
            else -> return true
        }
        val chartId = forwardProperties.group.qqTelegram[event.subject.id] ?: forwardProperties.group.defaultTelegram
        val caption = "[URL](${url.format2Markdown()}) \\- [SAUCE_NAO](${sauce_nao.format2Markdown()}) \\- [ASCII2D](${asscii2d.format2Markdown()})"
        try {
            telegramBotClient.execute(SendMessage.builder().chatId(chartId.toString()).text(sauce_nao).build())
        } catch (e: Exception) {
            log.debug("caption: $caption")
            log.error(e.message, e)
        }
        return true
    }

    private fun getSuffix(path: String?): String {
        return path?.substring(path.lastIndexOf('.').plus(1)) ?: ""
    }

    private fun overMaxQueryTimes(event: GroupAwareMessageEvent): Boolean {
        val id = event.sender.id
        val count = cache[id] ?: 0.also { cache.put(id, it + 1, 5, TimeUnit.MINUTES) }
        if ((count > 10)) {
            val builder = MessageChainBuilder()
            builder.add(event.message.quote())
            builder.add("Too much request, please try again in 5 minutes.")
            return true
        }
        return false
    }

    companion object {
        private const val ASCII2D = "https://ascii2d.net/search/url/%s?type=color";
        private const val SAUCE_NAO = "https://saucenao.com/search.php?db=999&dbmaski=32768&url=%s"
    }

    override fun order(): Int {
        return 50
    }
}