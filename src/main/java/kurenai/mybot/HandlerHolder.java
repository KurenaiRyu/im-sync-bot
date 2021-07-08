package kurenai.mybot;

import kurenai.mybot.handler.Handler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class HandlerHolder {

    private final List<Handler> handlerList; //初始化时处理器列表

    private List<Handler> currentHandlerList; //当前处理器列表

    public HandlerHolder(@Lazy List<Handler> handlerList) {
        this.handlerList = handlerList;
    }

    public List<Handler> getCurrentHandlerList() {
        if (currentHandlerList == null) {
            synchronized (TelegramBotClient.class) {
                if (currentHandlerList == null) {
                    currentHandlerList = handlerList.stream()
                            .sorted(Comparator.comparing(Handler::order).reversed()).collect(Collectors.toList());
                    log.info("current handler: {}", currentHandlerList.stream().map(Handler::getHandlerName).collect(Collectors.joining(", ")));
                }
            }
        }
        return currentHandlerList;
    }
}
