package kurenai.mybot.handler.impl;

import io.ktor.util.collections.ConcurrentList;
import kurenai.mybot.QQBotClient;
import kurenai.mybot.TelegramBotClient;
import kurenai.mybot.handler.Handler;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import net.mamoe.mirai.internal.message.OnlineGroupImage;
import net.mamoe.mirai.internal.utils.ExternalResourceImplByByteArray;
import net.mamoe.mirai.message.MessageReceipt;
import net.mamoe.mirai.message.data.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaAnimation;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "bot.handler.forward", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ForwardHandlerProperties.class)
@SuppressWarnings("KotlinInternalInJava")
public class ForwardHandler implements Handler {

    private final Map<Integer, MessageSource> tgQQMsgIdCache = new WeakHashMap<>();   // tg - qq message id cache;
    private final Map<Integer, Integer>       qqTgMsgIdCache = new WeakHashMap<>();   // qq - tg message id cache;
    private final ExecutorService             uploadPool     = Executors.newFixedThreadPool(10);
    private final ExecutorService             cachePool      = Executors.newFixedThreadPool(1);
    private final ForwardHandlerProperties    properties;

    public ForwardHandler(ForwardHandlerProperties properties) {
        this.properties = properties;
    }

    // tg 2 qq
    @Override
    public boolean handle(TelegramBotClient client, QQBotClient qqClient, Update update, Message message) {
        var chatId = message.getChatId();
        var bot    = qqClient.getBot();
        var quoteMsgSource = Optional.ofNullable(message.getReplyToMessage())
                .map(Message::getMessageId)
                .map(tgQQMsgIdCache::get);
        var groupId = quoteMsgSource.map(MessageSource::getTargetId)
                .orElse(properties.getGroup().getTelegramQQ().getOrDefault(chatId, properties.getGroup().getDefaultQQ()));
        Group group = bot.getGroup(groupId);
        if (group == null) {
            log.error("QQ group[{}] not found.", groupId);
            return true;
        }
//        if (message.hasAnimation()) {
//            Animation recMsg = message.getAnimation();
//            File    file    = new File();
//            file.setFileId(recMsg.getFileId());
//            file.setFileUniqueId(recMsg.getFileUniqueId());
//            try {
//                file.setFilePath(client.execute(GetFile.builder().fileId(file.getFileId()).build()).getFilePath());
//            } catch (TelegramApiException e) {
//                log.error(e.getMessage(), e);
//            }
//            String suffix = file.getFilePath().substring(file.getFilePath().lastIndexOf('.') + 1);
//            try (InputStream is = client.downloadFileAsStream(file);
//                 ExternalResource er = new ExternalResourceImplByByteArray(is.readAllBytes(), suffix)) {
//                Image               image   = bot.getGroup(groupId).uploadImage(er);
//                MessageChainBuilder builder = new MessageChainBuilder();
//                builder.add(image);
//                Optional.ofNullable(message.getCaption())
//                        .or(() -> Optional.ofNullable(message.getText()))
//                        .ifPresent(builder::add);
//                Optional.ofNullable(bot.getGroup(groupId)) //bot test group
//                        .ifPresent(g -> g.sendMessage(builder.build()));
//                log.debug("image: {}", ((OfflineGroupImage) image).getUrl(bot));
//            } catch (TelegramApiException | IOException e) {
//                log.error(e.getMessage(), e);
//            };
        if (message.hasSticker()) {
            Sticker sticker = message.getSticker();
            if (sticker.getIsAnimated()) {
                return true;
            }

            MessageChainBuilder builder = new MessageChainBuilder();
            quoteMsgSource.map(QuoteReply::new).ifPresent(builder::add);
            getImage(client, group, sticker.getFileId(), sticker.getFileUniqueId())
                    .ifPresent(builder::add);
            Optional.ofNullable(message.getCaption())
                    .or(() -> Optional.ofNullable(message.getText()))
                    .ifPresent(builder::add);
            MessageReceipt<?> receipt = group.sendMessage(builder.build());
            cachePool.execute(() -> {
                tgQQMsgIdCache.put(message.getMessageId(), receipt.getSource());
                qqTgMsgIdCache.put(receipt.getSource().getIds()[0], message.getMessageId());
            });
        } else if (message.hasPhoto()) {
            List<PhotoSize> photos = new ArrayList<>();
            message.getPhoto().stream()
                    .collect(Collectors.groupingBy(photoSize -> photoSize.getFileId().substring(0, 40)))
                    .forEach((id, photoSizes) -> photoSizes.stream().max(Comparator.comparing(PhotoSize::getFileSize)).ifPresent(photos::add));
            List<Image>           images  = new ConcurrentList<>();
            List<Future<Boolean>> futures = new ArrayList<>();
            for (PhotoSize photo : photos) {
                futures.add(uploadPool.submit(() -> {
                    getImage(client, group, photo.getFileId(), photo.getFileUniqueId()).ifPresent(images::add);
                    return true;
                }));
            }
            futures.forEach(f -> {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error(e.getMessage(), e);
                }
            });
            MessageChainBuilder builder = new MessageChainBuilder();
            quoteMsgSource.map(QuoteReply::new).ifPresent(builder::add);
            builder.addAll(images);
            Optional.ofNullable(message.getCaption()).ifPresent(builder::add);
            MessageReceipt<?> receipt = group.sendMessage(builder.build());
            cachePool.execute(() -> {
                tgQQMsgIdCache.put(message.getMessageId(), receipt.getSource());
                qqTgMsgIdCache.put(receipt.getSource().getIds()[0], message.getMessageId());
            });
        } else if (message.hasText()) {
            MessageChainBuilder builder = new MessageChainBuilder();
            quoteMsgSource.map(QuoteReply::new).ifPresent(builder::add);
            builder.add(message.getText());
            MessageReceipt<?> receipt = group.sendMessage(builder.build());
            cachePool.execute(() -> {
                tgQQMsgIdCache.put(message.getMessageId(), receipt.getSource());
                qqTgMsgIdCache.put(receipt.getSource().getIds()[0], message.getMessageId());
            });
        }
        return true;
    }

    // qq 2 tg
    @Override
    public boolean handle(QQBotClient client, TelegramBotClient telegramBotClient, GroupAwareMessageEvent event) {
        long              groupId      = event.getGroup().getId();
        MessageChain      messageChain = event.getMessage();

        var source = Optional.ofNullable(messageChain.get(MessageSource.Key));
        Optional<Integer> replyId = Optional.ofNullable(messageChain.get(QuoteReply.Key))
                .map(QuoteReply::getSource)
                .map(MessageSource::getIds)
                .map(ints -> ints[0])
                .map(qqTgMsgIdCache::get);
        String content = formatContent(messageChain.stream().filter(m -> !(m instanceof Image)).map(SingleMessage::contentToString).collect(Collectors.joining()));
        String msg = String.format("[%s] %s\r\n%s",
                handleLongString(event.getSenderName(), 25),
                event.getSender().getId(),
                content);
        String chatId = properties.getGroup().getQqTelegram().getOrDefault(groupId, properties.getGroup().getDefaultTelegram()).toString();

        long count = messageChain.stream().filter(m -> m instanceof OnlineGroupImage).count();
        if (count > 0) {
            if (count > 1) {
                List<InputMedia> medias = messageChain.stream().filter(m -> m instanceof OnlineGroupImage)
                        .map(i -> {
                            OnlineGroupImage image = (OnlineGroupImage) i;
                            String           url   = image.getOriginUrl();
                            if (image.getImageId().endsWith(".gif")) {
                                return new InputMediaAnimation(url);
                            } else {
                                return new InputMediaPhoto(url);
                            }
                        })
                        .collect(Collectors.toList());
                if (medias.size() > 0) {
                    InputMedia media = medias.get(0);
                    media.setCaption(msg);
                    var builder = SendMediaGroup.builder();
                    replyId.ifPresent(builder::replyToMessageId);
                    telegramBotClient.executeAsync(builder
                            .medias(medias)
                            .chatId(chatId)
                            .build()).handle((messages, throwable) -> {
                        if (throwable != null) {
                            log.error(throwable.getMessage(), throwable);
                        } else {
                            var messageId = messages.get(0).getMessageId();
                            source.ifPresent(s -> tgQQMsgIdCache.put(messageId, s));
                            source.map(MessageSource::getIds).map(ids -> ids[0]).ifPresent(id -> qqTgMsgIdCache.put(id, messageId));
                        }
                        return true;
                    });
                }
            } else {
                OnlineGroupImage image = (OnlineGroupImage) messageChain.get(Image.Key);
                if (image == null) return true;
                if (image.getImageId().endsWith(".gif")) {
                    var builder = SendAnimation.builder();
                    replyId.ifPresent(builder::replyToMessageId);
                    telegramBotClient.executeAsync(builder
                            .caption(msg)
                            .chatId(chatId)
                            .animation(new InputFile(image.getOriginUrl()))
                            .build()).handle((message, throwable) -> {
                        if (throwable != null) {
                            log.error(throwable.getMessage(), throwable);
                        } else {
                            var messageId = message.getMessageId();
                            source.ifPresent(s -> tgQQMsgIdCache.put(messageId, s));
                            source.map(MessageSource::getIds).map(ids -> ids[0]).ifPresent(id -> qqTgMsgIdCache.put(id, messageId));
                        }
                        return true;
                    });
                } else {
                    telegramBotClient.executeAsync(SendPhoto.builder()
                            .caption(msg)
                            .chatId(chatId)
                            .photo(new InputFile(image.getOriginUrl()))
                            .build()).handle((message, throwable) -> {
                        if (throwable != null) {
                            log.error(throwable.getMessage(), throwable);
                        } else {
                            var messageId = message.getMessageId();
                            source.ifPresent(s -> tgQQMsgIdCache.put(messageId, s));
                            source.map(MessageSource::getIds).map(ids -> ids[0]).ifPresent(id -> qqTgMsgIdCache.put(id, messageId));
                        }
                        return true;
                    });
                }
            }

        } else {
            var builder = SendMessage.builder();
            replyId.ifPresent(builder::replyToMessageId);
            try {
                telegramBotClient.executeAsync(builder.chatId(chatId).text(msg).build())
                        .handle((message, throwable) -> {
                            if (throwable != null) {
                                log.error(throwable.getMessage(), throwable);
                            } else {
                                var messageId = message.getMessageId();
                                source.ifPresent(s -> tgQQMsgIdCache.put(messageId, s));
                                source.map(MessageSource::getIds).map(ids -> ids[0]).ifPresent(id -> qqTgMsgIdCache.put(id, messageId));
                            }
                            return true;
                        });
            } catch (TelegramApiException e) {
                log.error(e.getMessage(), e);
            }
        }
        log.debug("{}({}) - {}({}): {}", event.getGroup().getName(), groupId, event.getSenderName(), event.getSender().getId(), content);
        return true;
    }

    @Override
    public int order() {
        return 0;
    }

    @NotNull
    private Optional<Image> getImage(TelegramBotClient client, Group group, String fileId, String fileUniqueId) {
        File file = new File();
        file.setFileId(fileId);
        file.setFileUniqueId(fileUniqueId);
        try {
            file.setFilePath(client.execute(GetFile.builder().fileId(file.getFileId()).build()).getFilePath());
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
        String suffix = file.getFilePath().substring(file.getFilePath().lastIndexOf('.') + 1);
        try (var is = client.downloadFileAsStream(file);
             var er = new ExternalResourceImplByByteArray(is.readAllBytes(), suffix)) {

            return Optional.of(group.uploadImage(er));
        } catch (TelegramApiException | IOException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    private String formatContent(String msg) {
        return msg.replace("\\n", "\r\n");
    }

    private String handleLongString(String target, int maxLength) {
        if (target.length() > maxLength) {
            return target.substring(0, maxLength) + "...";
        }
        return target;
    }
}
