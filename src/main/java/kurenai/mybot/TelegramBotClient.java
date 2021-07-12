package kurenai.mybot;

import kurenai.mybot.handler.Handler;
import kurenai.mybot.telegram.TelegramBotProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

/**
 * 机器人实例
 * @author liufuhong
 * @since 2021-06-30 14:05
 */

@Slf4j
public class TelegramBotClient extends TelegramLongPollingBot {

  private final TelegramBotProperties telegramBotProperties;
  private final HandlerHolder handlerHolder; //初始化时处理器列表
  private final   ApplicationContext     context;
  public TelegramBotClient(DefaultBotOptions options, TelegramBotProperties telegramBotProperties, @Lazy HandlerHolder handlerHolder, ApplicationContext context) {
    super(options);
    this.telegramBotProperties = telegramBotProperties;
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
    log.trace("update: {}", update);

    // We check if the update has a message and the message has text
    if (update.hasMessage() && (update.getMessage().isGroupMessage() || update.getMessage().isSuperGroupMessage())) {
      Long    gid     = update.getMessage().getChatId();
      Message message = update.getMessage();

      Optional<Chat> chat = Optional.ofNullable(message.getChat());
      Optional<User> from = Optional.ofNullable(message.getFrom());
      log.info("{}({}) - {}({}): ({}) {}",
              chat.map(Chat::getTitle).orElse("Null"),
              chat.map(Chat::getId).orElse(0L),
              from.map(User::getFirstName).orElse(from.map(User::getLastName).orElse("Null")),
              from.map(User::getId).orElse(0L),
              message.getMessageId(), message.getText());

      for (Handler handler : handlerHolder.getCurrentHandlerList()) {
        if (!handler.handle(this, context.getBean(QQBotClient.class), update, message)) break;
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
