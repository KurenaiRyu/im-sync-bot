package kurenai.mybot.config;

import ch.qos.logback.core.joran.conditional.IfAction;
import kurenai.mybot.MyBot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.DefaultBotOptions.ProxyType;

/**
 * @author liufuhong
 * @since 2021-06-30 14:08
 */

@Configuration
@EnableConfigurationProperties({BotProperties.class, ProxyProperties.class})
public class BotConfiguration {

  @Bean
  public DefaultBotOptions defaultBotOptions(ProxyProperties proxyProperties) {
    DefaultBotOptions botOptions = new DefaultBotOptions();
    if (proxyProperties.getType().equals(ProxyType.NO_PROXY)) return botOptions;
    botOptions.setProxyType(proxyProperties.getType());
    botOptions.setProxyHost(proxyProperties.getHost());
    botOptions.setProxyPort(proxyProperties.getPort());
    return botOptions;
  }

  @Bean
  public MyBot myBot(DefaultBotOptions defaultBotOptions, BotProperties botProperties) {
    return new MyBot(defaultBotOptions, botProperties);
  }

}
