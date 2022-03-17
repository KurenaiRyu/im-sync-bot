package kurenai.imsyncbot.handler.qq

import kurenai.imsyncbot.config.GroupConfig
import kurenai.imsyncbot.config.GroupConfig.qqTg
import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
import kurenai.imsyncbot.handler.Handler.Companion.END
import kurenai.imsyncbot.handler.config.ForwardHandlerProperties
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.request.message.SendPhoto
import mu.KotlinLogging
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.RichMessage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
@EnableConfigurationProperties(ForwardHandlerProperties::class)
@ConditionalOnProperty(prefix = "bot.handler.pic-source", name = ["enable"], havingValue = "true")
class PicSourceHandler(
    private val forwardProperties: ForwardHandlerProperties,
    private val cacheService: CacheService,
) : QQHandler {

    private val log = KotlinLogging.logger {}

    @Throws(Exception::class)
    override suspend fun onGroupMessage(event: GroupAwareMessageEvent): Int {
        val message = event.message
        val url = message[Image.Key]?.queryUrl() ?: return CONTINUE
        val content = message.contentToString()
        val encodeUrl = URLEncoder.encode(url, StandardCharsets.UTF_8)
        val sauce_nao = String.format(SAUCE_NAO, encodeUrl)
        val asscii2d = String.format(ASCII2D, encodeUrl)
        var matched = false
        if (content.contains("source", true)) {
            matched = true
            event.subject.sendMessage(RichMessage.Key.share(asscii2d, "Asscii2d搜索结果", "", url))
            event.subject.sendMessage(RichMessage.Key.share(sauce_nao, "SauceNAO搜索结果", "", url))
        }
        if (content.contains("url", true)) {
            matched = true
            val builder = MessageChainBuilder()
            builder.add(message.quote())
            builder.add(RichMessage.Key.share(url, "QQ图片URL", "", url))
            event.subject.sendMessage(builder.build())
        }
        if (!matched) return CONTINUE
        val chartId = qqTg[event.subject.id] ?: GroupConfig.defaultTgGroup
        val caption = "[SAUCE\\_NAO](${sauce_nao})\n[ASCII2D](${asscii2d})"
        try {
            SendPhoto(chartId.toString(), InputFile(url)).apply {
                this.caption = caption
                this.parseMode = ParseMode.MARKDOWN_V2
            }.send()
        } catch (e: Exception) {
            log.debug("caption: $caption")
            log.error(e.message, e)
        }
        return END
    }

    companion object {
        private const val ASCII2D = "https://ascii2d.net/search/url/%s?type=color";
        private const val SAUCE_NAO = "https://saucenao.com/search.php?db=999&dbmaski=32768&url=%s"
    }
}