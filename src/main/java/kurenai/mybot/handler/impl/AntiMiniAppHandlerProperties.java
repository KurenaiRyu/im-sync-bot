package kurenai.mybot.handler.impl;

import kurenai.mybot.handler.HandlerProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "bot.handler.anti-mini-app")
public class AntiMiniAppHandlerProperties extends HandlerProperties {

    private List<Long> ignoreGroup;
}
