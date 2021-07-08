package kurenai.mybot.handler.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import kurenai.mybot.QQBotClient;
import kurenai.mybot.TelegramBotClient;
import kurenai.mybot.handler.Handler;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "bot.handler.anti-mini-app", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AntiMiniAppHandlerProperties.class)
public class AntiMiniAppHandler implements Handler {

    private static final String ANTI_PREFIX = "Anti mini app or share link by qq!\r\n";

    private final ObjectMapper                 jsonMapper = new ObjectMapper();
    private final XmlMapper                    xmlMapper  = new XmlMapper();
    private final AntiMiniAppHandlerProperties properties;
    private final ForwardHandlerProperties     forwardProperties;

    public AntiMiniAppHandler(AntiMiniAppHandlerProperties properties, ForwardHandlerProperties forwardProperties) {
        this.properties = properties;
        this.forwardProperties = forwardProperties;
    }

    @Override
    public boolean handle(QQBotClient client, TelegramBotClient telegramBotClient, GroupAwareMessageEvent event) {
        var id        = event.getSubject().getId();
        var chatId = forwardProperties.getGroup().getQqTelegram().getOrDefault(id, forwardProperties.getGroup().getDefaultTelegram());

        var ignoreGroup = properties.getIgnoreGroup();
        if (ignoreGroup != null && !ignoreGroup.isEmpty() && ignoreGroup.contains(id)) return true;

        var content = event.getMessage().contentToString();
        if (content.startsWith("<?xml version='1.0'")) {
            log.debug("starting parse xml: {}", content);
            try {
                var jsonNode = xmlMapper.readTree(content);
                var action   = jsonNode.get("action").asText("");
                if (action.equals("web")) {
                    var title = "title: " + Optional.ofNullable(jsonNode.get("title"))
                            .map(JsonNode::asText)
                            .or(() -> Optional.ofNullable(jsonNode.get("item"))
                                    .map(i -> i.get("summary"))
                                    .map(JsonNode::asText))
                            .orElse("");
                    var url = "url:   " + Optional.ofNullable(jsonNode.get("url")).map(JsonNode::asText).orElse("");
                    event.getSubject().sendMessage(String.join("\r\n", ANTI_PREFIX, title, url));
                    sendTg(telegramBotClient, chatId.toString(), url);
                    return false;
                }
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        } else if (content.contains("\"app\":")) {
            log.debug("starting parse json: {}", content);
            try {
                var title    = "title: ";
                var url      = "url:   ";
                var jsonNode = jsonMapper.readTree(content);
                if (jsonNode.get("view").asText("").equals("news")) {
                    var news = Optional.ofNullable(jsonNode.get("meta")).map(j -> j.get("news"));
                    title += news.map(n -> n.get("title")).map(JsonNode::asText).orElse("");
                    url += news.map(n -> n.get("jumpUrl")).map(JsonNode::asText).orElse("");
                } else {
                    var item = Optional.ofNullable(jsonNode.get("meta")).map(j -> j.get("detail_1"));
                    title += item.map(m -> m.get("desc")).map(JsonNode::asText).orElse("");
                    url += item.map(m -> m.get("qqdocurl")).map(JsonNode::asText).orElse("");
                }
                event.getSubject().sendMessage(String.join("\r\n", ANTI_PREFIX, title, handleUrl(url)));
                sendTg(telegramBotClient, chatId.toString(), url);
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        }
        return true;
    }

    @Override
    public int order() {
        return 50;
    }

    private void sendTg(TelegramBotClient client, String chatId, String url) {
        try {
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(url)
                    .build()
            );
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }

    private String handleUrl(String url) {
        if (StringUtils.isNotBlank(url) && url.contains("?") && url.contains("b23.tv")) {
            return url.substring(0, url.indexOf("?"));
        }
        return url;
    }
}
