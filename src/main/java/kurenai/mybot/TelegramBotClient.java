package kurenai.mybot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kurenai.mybot.handler.Handler;
import kurenai.mybot.telegram.TelegramBotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

/**
 * 机器人实例
 *
 * @author liufuhong
 * @since 2021-06-30 14:05
 */

@Slf4j
public class TelegramBotClient extends TelegramLongPollingBot {

    private final ObjectMapper          mapper = new ObjectMapper();
    private final TelegramBotProperties telegramBotProperties;
    private final BanProperties         banProperties;
    private final HandlerHolder         handlerHolder; //初始化时处理器列表
    private final ApplicationContext    context;

    public TelegramBotClient(DefaultBotOptions options, TelegramBotProperties telegramBotProperties, BanProperties banProperties, @Lazy HandlerHolder handlerHolder, ApplicationContext context) {
        super(options);
        this.telegramBotProperties = telegramBotProperties;
        this.banProperties = banProperties;
        this.handlerHolder = handlerHolder;
        this.context = context;
    }

    @Override
    public String getBotUsername() {
        return telegramBotProperties.getUsername();
    }

    @Override
    public String getBotToken() {
        return telegramBotProperties.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            log.debug("onUpdateReceived: {}", mapper.writeValueAsString(update));
        } catch (JsonProcessingException e) {
            log.debug("onUpdateReceived: {}", update);
        }

        if (update.hasMessage() && (update.getMessage().isGroupMessage() || update.getMessage().isSuperGroupMessage()) ||
                update.hasEditedMessage() && (update.getEditedMessage().isSuperGroupMessage() || update.getEditedMessage().isGroupMessage())) {

            var qqBotClient = context.getBean(QQBotClient.class);

            Long       chatId   = update.getMessage().getChatId();
            Long       senderId = update.getMessage().getFrom().getId();
            List<Long> banGroup = banProperties.getGroup();
            if (banGroup != null && !banGroup.isEmpty()) {
                if (banGroup.contains(chatId)) {
                    return;
                }
            }

            List<Long> banMember = banProperties.getMember();
            if (banMember != null && !banMember.isEmpty()) {
                if (banMember.contains(senderId)) {
                    return;
                }
            }

            if (update.hasMessage()) {
                for (Handler handler : handlerHolder.getCurrentHandlerList()) {
                    try {
                        if (!handler.handleMessage(this, qqBotClient, update, update.getMessage())) break;
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            } else if (update.hasEditedMessage()) {
                for (Handler handler : handlerHolder.getCurrentHandlerList()) {
                    try {
                        if (!handler.handleEditMessage(this, qqBotClient, update, update.getEditedMessage())) break;
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }
    }


    @Override
    public void onRegister() {
        try {
            User me = getMe();
            if (me != null) {
                log.info("Started telegram-bot: {}({}, {}).", me.getFirstName().equalsIgnoreCase("null") ? me.getLastName() : me.getFirstName(), me.getUserName(), me.getId());
            } else {
                log.info("Started telegram-bot: {}.", getBotUsername());
            }
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }


}
