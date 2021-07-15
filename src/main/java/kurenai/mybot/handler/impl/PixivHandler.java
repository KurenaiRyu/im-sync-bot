package kurenai.mybot.handler.impl;

import kurenai.mybot.QQBotClient;
import kurenai.mybot.TelegramBotClient;
import kurenai.mybot.handler.Handler;
import kurenai.mybot.handler.config.ForwardHandlerProperties;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.regex.Pattern;

@Component
@Slf4j
@EnableConfigurationProperties(ForwardHandlerProperties.class)
@ConditionalOnProperty(prefix = "bot.handler.pixiv", name = "enable", havingValue = "true")
public class PixivHandler implements Handler {

    private static final String PIXIV_ART_PREFIX  = "https://www.pixiv.net/artworks/";
    private static final String PIXIV_USER_PREFIX = "https://www.pixiv.net/users/";

    private final ForwardHandlerProperties forwardProperties;
    Pattern pattern = Pattern.compile("^pixiv \\d+");

    public PixivHandler(ForwardHandlerProperties forwardProperties) {
        this.forwardProperties = forwardProperties;
    }

    @Override
    public boolean handle(QQBotClient client, TelegramBotClient telegramBotClient, GroupAwareMessageEvent event) throws Exception {
        final var content = event.getMessage().serializeToMiraiCode();
        final var matcher = pattern.matcher(content);
        if (matcher.find()) {
            var chartId = forwardProperties.getGroup().getQqTelegram().getOrDefault(event.getSubject().getId(), forwardProperties.getGroup().getDefaultTelegram()).toString();

            final var id      = content.substring(6);
            final var artUrl  = PIXIV_ART_PREFIX + id;
            final var userUrl = PIXIV_USER_PREFIX + id;
            event.getSubject().sendMessage(artUrl);
            event.getSubject().sendMessage(userUrl);
            try {
                telegramBotClient.execute(SendMessage.builder().chatId(chartId).text(artUrl).build());
                telegramBotClient.execute(SendMessage.builder().chatId(chartId).text(userUrl).build());
            } catch (TelegramApiException e) {
                log.error(e.getMessage(), e);
            }
            return false;
        }
        return true;
    }

    @Override
    public int order() {
        return 50;
    }
}
