package kurenai.mybot

import kurenai.mybot.cache.Cache
import net.mamoe.mirai.message.data.OnlineMessageSource
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Message
import java.util.concurrent.TimeUnit

@Component
object CacheHolder {
    val QQ_MSG_CACHE = Cache<Int?, OnlineMessageSource?>("QQ_MSG_CACHE")
    val TG_MSG_CACHE = Cache<Int?, Message?>("TG_MSG_CACHE")
    val TG_QQ_MSG_ID_CACHE = Cache<Int?, Int?>("TG_QQ_MSG_ID_CACHE") // tg - qq message id cache;
    val QQ_TG_MSG_ID_CACHE = Cache<Int?, Int?>("QQ_TG_MSG_ID_CACHE") // qq - tg message id cache;
    suspend fun cache(source: OnlineMessageSource, message: Message) {
        val qqMsgId = source.ids[0]
        val tgMsgId = message.messageId
        QQ_TG_MSG_ID_CACHE.put(qqMsgId, tgMsgId, 1, TimeUnit.DAYS)
        TG_QQ_MSG_ID_CACHE.put(tgMsgId, qqMsgId, 1, TimeUnit.DAYS)
        QQ_MSG_CACHE.put(qqMsgId, source, 1, TimeUnit.DAYS)
        TG_MSG_CACHE.put(tgMsgId, message, 1, TimeUnit.DAYS)
    }
}