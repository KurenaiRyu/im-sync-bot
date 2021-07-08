package kurenai.mybot;

import net.mamoe.mirai.message.data.OnlineMessageSource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Map;
import java.util.WeakHashMap;

@Component
public class CacheHolder {

    public static final Map<Integer, OnlineMessageSource> QQ_MSG_CACHE       = new WeakHashMap<>();
    public static final Map<Integer, Message>             TG_MSG_CACHE       = new WeakHashMap<>();
    public static final Map<Integer, Integer>             TG_QQ_MSG_ID_CACHE = new WeakHashMap<>();   // tg - qq message id cache;
    public static final Map<Integer, Integer>             QQ_TG_MSG_ID_CACHE = new WeakHashMap<>();   // qq - tg message id cache;

    public static void cache(OnlineMessageSource source, Message message) {
        var qqMsgId = source.getIds()[0];
        var tgMsgId = message.getMessageId();
        QQ_TG_MSG_ID_CACHE.put(qqMsgId, tgMsgId);
        TG_QQ_MSG_ID_CACHE.put(tgMsgId, qqMsgId);
        QQ_MSG_CACHE.put(qqMsgId, source);
        TG_MSG_CACHE.put(tgMsgId, message);
    }

}
