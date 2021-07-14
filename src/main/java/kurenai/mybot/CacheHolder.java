package kurenai.mybot;

import kurenai.mybot.cache.Cache;
import net.mamoe.mirai.message.data.OnlineMessageSource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.concurrent.TimeUnit;

@Component
public class CacheHolder {

    public static final Cache<Integer, OnlineMessageSource> QQ_MSG_CACHE       = new Cache<>("QQ_MSG_CACHE");
    public static final Cache<Integer, Message>             TG_MSG_CACHE       = new Cache<>("TG_MSG_CACHE");
    public static final Cache<Integer, Integer>             TG_QQ_MSG_ID_CACHE = new Cache<>("TG_QQ_MSG_ID_CACHE");   // tg - qq message id cache;
    public static final Cache<Integer, Integer>             QQ_TG_MSG_ID_CACHE = new Cache<>("QQ_TG_MSG_ID_CACHE");   // qq - tg message id cache;

    public static void cache(OnlineMessageSource source, Message message) {
        var qqMsgId = source.getIds()[0];
        var tgMsgId = message.getMessageId();
        QQ_TG_MSG_ID_CACHE.put(qqMsgId, tgMsgId, 1, TimeUnit.DAYS);
        TG_QQ_MSG_ID_CACHE.put(tgMsgId, qqMsgId, 1, TimeUnit.DAYS);
        QQ_MSG_CACHE.put(qqMsgId, source, 1, TimeUnit.DAYS);
        TG_MSG_CACHE.put(tgMsgId, message, 1, TimeUnit.DAYS);
    }

}
