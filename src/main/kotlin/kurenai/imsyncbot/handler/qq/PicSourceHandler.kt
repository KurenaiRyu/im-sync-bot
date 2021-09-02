package kurenai.imsyncbot.handler.qq

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.handler.Handler.Companion.BREAK
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import kurenai.imsyncbot.service.CacheService
import mu.KotlinLogging
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.RichMessage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Component
@EnableConfigurationProperties(ForwardHandlerProperties::class)
@ConditionalOnProperty(prefix = "bot.handler.pic-source", name = ["enable"], havingValue = "true")
class PicSourceHandler(
    private val forwardProperties: ForwardHandlerProperties,
    private val cacheService: CacheService,
) : QQHandler {

    private val log = KotlinLogging.logger {}
    private val cache = kurenai.imsyncbot.cache.Cache<Long, Int>("pic-source")

    @Throws(Exception::class)
    override suspend fun onGroupMessage(event: GroupAwareMessageEvent): Int {
        val message = event.message
        val image = when {
            message.contains(Image.Key) -> {
                message[Image.Key]!!
            }
            message.contains(QuoteReply.Key) -> {
                val quoteReply = message[QuoteReply.Key]!!
                quoteReply.source.ids[0].let {
                    cacheService.getQQ(it)
                }?.originalMessage?.get(Image.Key)
            }
            else -> null
        } ?: return CONTINUE
        val content = message.contentToString()
        val url = image.queryUrl()
        val encodeUrl = URLEncoder.encode(url, StandardCharsets.UTF_8)
        val sauce_nao = String.format(SAUCE_NAO, encodeUrl)
        val asscii2d = String.format(ASCII2D, encodeUrl)
        var matched = false
        if (content.contains("source", true)) {
            matched = true
            if (overMaxQueryTimes(event)) return BREAK
            event.subject.sendMessage(message.quote().plus(RichMessage.Key.share(asscii2d, "Asscii2d搜索结果", "", url)))
            event.subject.sendMessage(message.quote().plus(RichMessage.Key.share(sauce_nao, "SauceNAO搜索结果", "", url)))
        }
        if (content.contains("url", true)) {
            matched = true
            if (overMaxQueryTimes(event)) return BREAK
            val builder = MessageChainBuilder()
            builder.add(message.quote())
            builder.add(RichMessage.Key.share(url, "QQ图片URL", "", url))
            event.subject.sendMessage(builder.build())
        }
        if (!matched) return CONTINUE
        val chartId = forwardProperties.group.qqTelegram[event.subject.id] ?: ContextHolder.defaultTgGroup
        val caption = "[SAUCE\\_NAO](${sauce_nao})\n[ASCII2D](${asscii2d})"
        try {
            ContextHolder.telegramBotClient.execute(
                SendPhoto.builder().chatId(chartId.toString()).caption(caption).photo(InputFile(url)).parseMode(ParseMode.MARKDOWNV2)
                    .build()
            )
        } catch (e: Exception) {
            log.debug("caption: $caption")
            log.error(e.message, e)
        }
        return BREAK
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
}