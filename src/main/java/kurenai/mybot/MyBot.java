package kurenai.mybot;

import java.io.File;
import kurenai.mybot.config.BotProperties;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * 机器人实例
 * @author liufuhong
 * @since 2021-06-30 14:05
 */

public class MyBot extends TelegramLongPollingBot {

  private final Logger log = LoggerFactory.getLogger(MyBot.class);

  private final BotProperties botProperties;

  public MyBot(DefaultBotOptions options, BotProperties botProperties) {
    super(options);
    this.botProperties = botProperties;
  }

  @Override
  public String getBotUsername() {
    return botProperties.getUsername();
  }

  @Override
  public String getBotToken() {
    return botProperties.getToken();
  }

  @Override
  public void onUpdateReceived(Update update) {
    log.trace("update: {}", update);

    // We check if the update has a message and the message has text
    if (update.hasMessage() && update.getMessage().isGroupMessage()) {
      Long gid = update.getMessage().getChatId();
      Message recMsg = update.getMessage();
      if (recMsg.hasText()) {
        SendMessage message = new SendMessage(); // Create a SendMessage object with mandatory fields
        message.setChatId(recMsg.getChatId().toString());
        message.setText(recMsg.getText());

        try {
          execute(message); // Call method to send the message
        } catch (TelegramApiException e) {
          log.error(e.getMessage(), e);
        }
      } else if (recMsg.hasSticker()) {
        File file = download(recMsg.getSticker().getFileUniqueId());
        if (file != null) {
          SendPhoto photo = new SendPhoto();
          photo.setPhoto(new InputFile(file));
          photo.setChatId(gid.toString());
        }
      }
    }
  }

  @Override
  public void onRegister() {
    super.onRegister();
  }

  private File download(@NonNull String fileId) {
    try {
      return downloadFile(fileId, new File(fileId));
    } catch (TelegramApiException e) {
      log.error("Download file error!", e);
    }
    return null;
  }
}
