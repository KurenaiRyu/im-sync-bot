package kurenai.mybot.handler.impl;

import kurenai.mybot.QQBotClient;
import kurenai.mybot.TelegramBotClient;
import kurenai.mybot.handler.Handler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@ConditionalOnProperty(prefix = "bot.handler.echo", name = "enable", havingValue = "true")
@Slf4j
public class EchoHandler implements Handler {

    @Override
    public boolean handleMessage(TelegramBotClient client, QQBotClient qqBotClient, Update update, Message message) throws Exception {

        if (message.hasText()) {
            SendMessage sendMessage = new SendMessage(); // Create a SendMessage object with mandatory fields
            sendMessage.setChatId(message.getChatId().toString());
            sendMessage.setText(message.getText());
            sendMessage.setReplyToMessageId(message.getMessageId());
            try {
                client.execute(sendMessage);
            } catch (TelegramApiException e) {
                log.error(e.getMessage(), e);
            }
            return true;
        }
        return true;
    }

    @Override
    public int order() {
        return 0;
    }
}
