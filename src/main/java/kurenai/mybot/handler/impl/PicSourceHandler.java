package kurenai.mybot.handler.impl;

import kurenai.mybot.CacheHolder;
import kurenai.mybot.QQBotClient;
import kurenai.mybot.TelegramBotClient;
import kurenai.mybot.handler.Handler;
import kurenai.mybot.handler.config.ForwardHandlerProperties;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.OnlineMessageSource;
import net.mamoe.mirai.message.data.QuoteReply;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Component
@EnableConfigurationProperties(ForwardHandlerProperties.class)
@ConditionalOnProperty(prefix = "bot.handler.pic-source", name = "enable", havingValue = "true")
public class PicSourceHandler implements Handler {

    //    private static final String ASCII2D   = "https://ascii2d.net/search/url/%s?type=color";
    private static final String SAUCE_NAO = "https://saucenao.com/search.php?db=999&dbmaski=32768&url=%s";

    private final ForwardHandlerProperties forwardProperties;

    public PicSourceHandler(ForwardHandlerProperties forwardProperties) {
        this.forwardProperties = forwardProperties;
    }

    @Override
    public boolean handle(QQBotClient client, TelegramBotClient telegramBotClient, GroupAwareMessageEvent event) throws Exception {
        if (Optional.of(event.getMessage())
                .map(MessageChain::contentToString)
                .filter(m -> m.contains("source") || m.contains("Source"))
                .isPresent()) {
            Optional.ofNullable(event.getMessage().get(Image.Key))
                    .or(() ->
                            Optional.ofNullable(event.getMessage().get(QuoteReply.Key))
                                    .map(QuoteReply::getSource)
                                    .map(source -> source.getIds()[0])
                                    .map(CacheHolder.QQ_MSG_CACHE::get)
                                    .map(OnlineMessageSource::getOriginalMessage)
                                    .map(m -> m.get(Image.Key))
                    )
                    .map(Image::queryUrl)
                    .map(u -> URLEncoder.encode(u, StandardCharsets.UTF_8))
                    .ifPresent(url -> {
                        var contact = event.getSubject();
                        var chartId = forwardProperties.getGroup().getQqTelegram().getOrDefault(contact.getId(), forwardProperties.getGroup().getDefaultTelegram()).toString();

//                        var ascii2d  = String.format(ASCII2D, url);
                        var sauceNao = String.format(SAUCE_NAO, url);
//                        contact.sendMessage(ascii2d);
                        var quoteReply = new QuoteReply(event.getMessage());
                        contact.sendMessage(quoteReply.plus(sauceNao));

                        try {
//                            telegramBotClient.execute(SendMessage.builder().chatId(chartId).text(ascii2d).build());
                            telegramBotClient.execute(SendMessage.builder().chatId(chartId).text(sauceNao).build());
                        } catch (TelegramApiException e) {
                            log.error(e.getMessage(), e);
                        }
                    });

        }
        return true;
    }
}
