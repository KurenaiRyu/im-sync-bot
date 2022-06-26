//package kurenai.imsyncbot.handler.qq
//
//import kurenai.imsyncbot.config.GroupConfig
//import kurenai.imsyncbot.config.GroupConfig.qqTg
//import kurenai.imsyncbot.handler.Handler.Companion.CONTINUE
//import kurenai.imsyncbot.handler.Handler.Companion.END
//import kurenai.imsyncbot.handler.ForwardHandlerProperties
//import kurenai.imsyncbot.telegram.send
//import moe.kurenai.tdlight.exception.TelegramApiException
//import moe.kurenai.tdlight.request.message.SendMessage
//import mu.KotlinLogging
//import net.mamoe.mirai.event.events.GroupAwareMessageEvent
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
//import org.springframework.boot.context.properties.EnableConfigurationProperties
//import org.springframework.stereotype.Component
//import java.util.regex.Pattern
//
//@Component
//@EnableConfigurationProperties(ForwardHandlerProperties::class)
//@ConditionalOnProperty(prefix = "bot.handler.pixiv", name = ["enable"], havingValue = "true")
//class PixivHandler(private val forwardProperties: ForwardHandlerProperties) : QQHandler {
//
//    private val log = KotlinLogging.logger {}
//
//    var pattern: Pattern = Pattern.compile("^pixiv \\d+")
//
//    @Throws(Exception::class)
//    override suspend fun onGroupMessage(event: GroupAwareMessageEvent): Int {
//        val content = event.message.serializeToMiraiCode()
//        val matcher = pattern.matcher(content)
//        if (matcher.find()) {
//            val chartId = qqTg.getOrDefault(event.subject.id, GroupConfig.defaultTgGroup).toString()
//            val id = content.substring(6)
//            val artUrl = PIXIV_ART_PREFIX + id
//            val userUrl = PIXIV_USER_PREFIX + id
//            event.subject.sendMessage(artUrl)
//            event.subject.sendMessage(userUrl)
//            try {
//                SendMessage(chartId, artUrl).send()
//                SendMessage(chartId, userUrl).send()
//            } catch (e: TelegramApiException) {
//                log.error(e.message, e)
//            }
//            return END
//        }
//        return CONTINUE
//    }
//
//    override fun order(): Int {
//        return 50
//    }
//
//    companion object {
//        private const val PIXIV_ART_PREFIX = "https://www.pixiv.net/artworks/"
//        private const val PIXIV_USER_PREFIX = "https://www.pixiv.net/users/"
//    }
//}