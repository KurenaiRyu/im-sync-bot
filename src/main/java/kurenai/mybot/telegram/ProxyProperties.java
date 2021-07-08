package kurenai.mybot.telegram;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.telegram.telegrambots.bots.DefaultBotOptions.ProxyType;

/**
 * @author liufuhong
 * @since 2021-06-30 14:13
 */

@Data
@ConfigurationProperties(prefix = "bot.telegram.proxy")
public class ProxyProperties {
    private String    host = "localhost";
    private int       port = 1080;
    private ProxyType type = ProxyType.NO_PROXY;
}
