package kurenai.mybot

import kurenai.mybot.cache.Cache
import mu.KotlinLogging
import net.mamoe.mirai.message.data.OnlineMessageSource
import org.telegram.telegrambots.meta.api.objects.Message
import java.util.concurrent.TimeUnit

object CacheHolder {

    private val log = KotlinLogging.logger {}

    val QQ_MSG_CACHE = Cache<Int?, OnlineMessageSource?>("QQ_MSG", 10000)
    val TG_MSG_CACHE = Cache<Int?, Message?>("TG_MSG", 10000)
    val TG_QQ_MSG_ID_CACHE = Cache<Int?, Int?>("TG_QQ_MSG_ID", 10000) // tg - qq message id cache;
    val QQ_TG_MSG_ID_CACHE = Cache<Int?, Int?>("QQ_TG_MSG_ID", 10000) // qq - tg message id cache;

    fun cache(source: OnlineMessageSource, message: Message) {
        val qqMsgId = source.ids[0]
        val tgMsgId = message.messageId

        QQ_TG_MSG_ID_CACHE.put(qqMsgId, tgMsgId, 7, TimeUnit.DAYS)
        TG_QQ_MSG_ID_CACHE.put(tgMsgId, qqMsgId, 7, TimeUnit.DAYS)
        QQ_MSG_CACHE.put(qqMsgId, source, 7, TimeUnit.DAYS)
        TG_MSG_CACHE.put(tgMsgId, message, 7, TimeUnit.DAYS)
    }
}