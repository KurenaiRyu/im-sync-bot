package kurenai.mybot.handler.impl;

import kurenai.mybot.handler.HandlerProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "bot.handler.forward")
public class ForwardHandlerProperties extends HandlerProperties {

    private Group group;

    @PostConstruct
    public void init() {
        if (group.qqTelegram == null) group.qqTelegram = new HashMap<>();
        if (group.telegramQq == null) group.telegramQq = new HashMap<>();

        group.qqTelegram.forEach((k, v) -> group.telegramQq.putIfAbsent(v, k));
        group.telegramQq.forEach((k, v) -> group.qqTelegram.putIfAbsent(v, k));
    }

    @Getter
    @Setter
    static class Group {
        private Long            defaultQQ;
        private Long            defaultTelegram;
        private Map<Long, Long> qqTelegram;
        private Map<Long, Long> telegramQq;
    }
}

