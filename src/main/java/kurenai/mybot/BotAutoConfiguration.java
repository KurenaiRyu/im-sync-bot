package kurenai.mybot;

import kurenai.mybot.handler.Handler;
import kurenai.mybot.qq.QQBotProperties;
import kurenai.mybot.telegram.ProxyProperties;
import kurenai.mybot.telegram.TelegramBotProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.DefaultBotOptions.ProxyType;

import java.util.List;

/**
 * @author liufuhong
 * @since 2021-06-30 14:08
 */

@Configuration
@EnableConfigurationProperties({TelegramBotProperties.class, ProxyProperties.class, QQBotProperties.class})
public class BotAutoConfiguration {

  @Bean
  public HandlerHolder handlerHolder(@Lazy List<Handler> handlerList) {
    return new HandlerHolder(handlerList);
  }

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
  public TelegramBotClient telegramBot(DefaultBotOptions defaultBotOptions, TelegramBotProperties telegramBotProperties, @Lazy HandlerHolder handlerHolder, ApplicationContext context) {
    return new TelegramBotClient(defaultBotOptions, telegramBotProperties, handlerHolder, context);
  }

  @Bean
  public QQBotClient qqBot(QQBotProperties properties, @Lazy HandlerHolder handlerHolder, ApplicationContext context) {
    return new QQBotClient(properties, handlerHolder, context);
  }

}
