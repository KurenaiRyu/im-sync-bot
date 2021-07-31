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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Getter
public class QQBotClient {


    private final Bot                bot;
    private final QQBotProperties    properties;
    private final BanProperties      banProperties;
    private final HandlerHolder      handlerHolder;
    private final ApplicationContext context;

    public QQBotClient(QQBotProperties properties, BanProperties banProperties, HandlerHolder handlerHolder, ApplicationContext context) {
        this.banProperties = banProperties;
        this.handlerHolder = handlerHolder;
        this.context = context;
        this.properties = properties;

        // 使用自定义配置
        bot = BotFactory.INSTANCE.newBot(properties.getAccount(), properties.getPassword(), new BotConfiguration() {{
            fileBasedDeviceInfo(); // 使用 device.json 存储设备信息
            setProtocol(Optional.ofNullable(properties.getProtocol()).orElse(MiraiProtocol.ANDROID_PAD)); // 切换协议
        }});
        bot.login();
        ExecutorService pool = Executors.newFixedThreadPool(1);
        pool.execute(this::run);
        log.info("Started qq-bot {}({})", bot.getBot().getNick(), bot.getId());
    }

    public void run() {
        EventChannel<BotEvent> groupMsgFilter = this.bot.getEventChannel().filter(event -> {
            long groupId;
            long senderId;
            if (event instanceof GroupMessageEvent) {
                groupId = ((GroupMessageEvent) event).getGroup().getId();
                senderId = ((GroupMessageEvent) event).getSender().getId();
            } else if (event instanceof GroupMessageSyncEvent) {
                groupId = ((GroupMessageSyncEvent) event).getGroup().getId();
                senderId = ((GroupMessageSyncEvent) event).getSender().getId();
            } else {
                // 只过滤群消息
                return false;
            }

            QQBotProperties.Filter f = properties.getFilter();
            if (f != null) {
                var fGroup = f.getGroup();
                if (fGroup != null && !fGroup.isEmpty()) {
                    if (!fGroup.contains(groupId)) return false;
                }
                var fQQ = f.getQq();
                if (fQQ != null && !fQQ.isEmpty()) {
                    if (!fQQ.contains(senderId)) return false;
                }
            }

            List<Long> banGroup = banProperties.getGroup();
            if (banGroup != null && !banGroup.isEmpty()) {
                if (banGroup.contains(groupId)) return false;
            }
            List<Long> banMember = banProperties.getMember();
            if (banMember != null && !banMember.isEmpty()) {
                if (banMember.contains(senderId)) return false;
            }

            return true;
        });
        ExecutorService pool = Executors.newFixedThreadPool(5);
        groupMsgFilter.subscribeAlways(GroupMessageEvent.class, event -> pool.execute(() -> {
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
        groupMsgFilter.subscribeAlways(GroupMessageSyncEvent.class, event -> pool.execute(() -> {
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
