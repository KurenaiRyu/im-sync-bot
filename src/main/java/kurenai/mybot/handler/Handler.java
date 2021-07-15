package kurenai.mybot.handler;

import kurenai.mybot.QQBotClient;
import kurenai.mybot.TelegramBotClient;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface Handler {


    default boolean preHandle(TelegramBotClient client, Update update) {
        return true;
    }

    /**
     * @param client
     * @param qqClient
     * @param update
     * @return true 为继续执行，false中断
     */
    default boolean handleMessage(TelegramBotClient client, QQBotClient qqClient, Update update, Message message) throws Exception {
        return true;
    }

    default boolean handleEditMessage(TelegramBotClient client, QQBotClient qqClient, Update update, Message message) throws Exception {
        return true;
    }

    default boolean postHandle(TelegramBotClient client, Update update, Message message) {
        return true;
    }

    default boolean handle(QQBotClient client, TelegramBotClient telegramBotClient, GroupAwareMessageEvent event) throws Exception {
        return true;
    }

    default int order() {
        return 100;
    }

    default String getHandlerName() {
        return this.getClass().getSimpleName();
    }

}
