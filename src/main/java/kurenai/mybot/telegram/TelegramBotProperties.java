package kurenai.mybot.telegram;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author liufuhong
 * @since 2021-06-30 14:10
 */

@Data
@ConfigurationProperties(prefix = "bot.telegram")
public class TelegramBotProperties {
  private String token;
  private String username;
  private String baseUrl;
}
