package kurenai.mybot;

import kurenai.mybot.handler.Handler;
import kurenai.mybot.qq.QQBotProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import net.mamoe.mirai.event.EventChannel;
import net.mamoe.mirai.event.events.BotEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageSyncEvent;
import net.mamoe.mirai.utils.BotConfiguration;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Getter
public class QQBotClient {


    private final Bot               bot;
    private final QQBotProperties   properties;
    private final HandlerHolder     handlerHolder;
    private final ApplicationContext context;

    public QQBotClient(QQBotProperties properties, HandlerHolder handlerHolder, ApplicationContext context) {
        this.handlerHolder = handlerHolder;
        this.context = context;
        this.properties = properties;

        // 使用自定义配置
        bot = BotFactory.INSTANCE.newBot(properties.getAccount(), properties.getPassword(), new BotConfiguration() {{
            fileBasedDeviceInfo(); // 使用 device.json 存储设备信息
            setProtocol(MiraiProtocol.ANDROID_PAD); // 切换协议
        }});
        bot.login();
        ExecutorService pool = Executors.newFixedThreadPool(1);
        pool.execute(this::run);
        log.info("Started qq-bot {}({})", bot.getBot().getNick(), bot.getId());
    }

    public void run() {
        EventChannel<BotEvent> filter = this.bot.getEventChannel().filter(event -> {
            if (properties.getFilter().getQq() == null || properties.getFilter().getQq().isEmpty()) {
                return true;
            }
            List<Long> filterList = properties.getFilter().getQq();
            if (event instanceof GroupMessageEvent) {
                long       id   = ((GroupMessageEvent) event).getGroup().getId();
                return filterList.contains(id);
            } else if (event instanceof GroupMessageSyncEvent) {
                GroupMessageSyncEvent syncEvent = (GroupMessageSyncEvent) event;
                return filterList.contains(syncEvent.getGroup().getId());
            }
            return false;
        });
        ExecutorService pool = Executors.newFixedThreadPool(5);
        filter.subscribeAlways(GroupMessageEvent.class, event -> pool.execute(() -> {
            for (Handler handler : handlerHolder.getCurrentHandlerList()) {
                try {
                    if (!handler.handle(this, context.getBean(TelegramBotClient.class), event)) {
                        break;
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }));
        filter.subscribeAlways(GroupMessageSyncEvent.class, event -> pool.execute(() -> {
            for (Handler handler : handlerHolder.getCurrentHandlerList()) {
                try {
                    if (!handler.handle(this, context.getBean(TelegramBotClient.class), event)) {
                        break;
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }));
    }
}
