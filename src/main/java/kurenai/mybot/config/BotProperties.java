package kurenai.mybot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author liufuhong
 * @since 2021-06-30 14:10
 */

@Data
@ConfigurationProperties(prefix = "bot")
public class BotProperties {
  private String token;
  private String username;
}
