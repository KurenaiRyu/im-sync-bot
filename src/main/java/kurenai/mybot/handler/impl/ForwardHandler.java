package kurenai.mybot.handler.impl;

import io.ktor.util.collections.ConcurrentList;
import kurenai.mybot.CacheHolder;
import kurenai.mybot.QQBotClient;
import kurenai.mybot.TelegramBotClient;
import kurenai.mybot.handler.Handler;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import net.mamoe.mirai.internal.message.OnlineGroupImage;
import net.mamoe.mirai.message.MessageReceipt;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.ExternalResource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaAnimation;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "bot.handler.forward", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ForwardHandlerProperties.class)
@SuppressWarnings("KotlinInternalInJava")
public class ForwardHandler implements Handler {

    private static final String QQ_PATTNER    = "{qq}";
    private static final String NAME_PATTNER  = "{name}";
    private static final String MSG_PATTNER   = "{msg}";
    private static final String TG_ID_PATTNER = "{id}";

    private final String                   webpCmdPattern;
    private final ExecutorService          uploadPool = Executors.newFixedThreadPool(10);
    private final ExecutorService          cachePool  = Executors.newFixedThreadPool(1);
    private final ForwardHandlerProperties properties;

    private String tgMsgFormat = "{name}: {msg}";

    public ForwardHandler(ForwardHandlerProperties properties) {
        this.properties = properties;
        String encoderPath;
        if (System.getProperties().getProperty("os.name").equalsIgnoreCase("Linux")) {
            encoderPath = new java.io.File("bin/dwebp").getPath();
        } else {
            encoderPath = new java.io.File("bin/dwebp.exe").getPath();
        }
        webpCmdPattern = encoderPath + " %s -o %s";
    }

    // tg 2 qq
    @Override
    public boolean handle(TelegramBotClient client, QQBotClient qqClient, Update update, Message message) throws Exception {
        final var chatId = message.getChatId();
        var       bot    = qqClient.getBot();
        var quoteMsgSource = Optional.ofNullable(message.getReplyToMessage())
                .map(Message::getMessageId)
                .map(CacheHolder.TG_QQ_MSG_ID_CACHE::get)
                .map(CacheHolder.QQ_MSG_CACHE::get);
        final var groupId = quoteMsgSource.map(MessageSource::getTargetId)
                .orElse(properties.getGroup().getTelegramQq().getOrDefault(chatId, properties.getGroup().getDefaultQQ()));
        if (groupId.equals(0L)) return true;
        Group group = bot.getGroup(groupId);
//        var   isMe = client.getBotUsername().equals(message.getFrom().getUserName());
        final var isMe     = false;
        final var username = getUsername(message);
        if (group == null) {
            log.error("QQ group[{}] not found.", groupId);
            return true;
        }
//        if (message.hasDocument()) {
//            var document = message.getDocument();
//            handleFile(client, group, document.getFileId(), document.getFileUniqueId());
//        } else if (message.hasVideo()) {
//            var video = message.getVideo();
//            handleFile(client, group, video.getFileId(), video.getFileUniqueId());
//        }
//        if (message.hasAnimation()) {
//            Animation recMsg = message.getAnimation();
//            File      file   = new File();
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
            preHandleMsg(quoteMsgSource, isMe, username, builder);
            getImage(client, group, sticker.getFileId(), sticker.getFileUniqueId())
                    .ifPresent(builder::add);
            Optional.ofNullable(message.getCaption())
                    .or(() -> Optional.ofNullable(message.getText()))
                    .ifPresent(builder::add);
            MessageReceipt<?> receipt = group.sendMessage(builder.build());
            cachePool.execute(() -> CacheHolder.cache(receipt.getSource(), message));
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
            preHandleMsg(quoteMsgSource, isMe, username, builder);
            builder.addAll(images);
            Optional.ofNullable(message.getCaption()).ifPresent(builder::add);
            MessageReceipt<?> receipt = group.sendMessage(builder.build());
            cachePool.execute(() -> CacheHolder.cache(receipt.getSource(), message));
        } else if (message.hasText()) {
            var text = message.getText();
            if (changeTgMsgFormat(text)) return false;

            MessageChainBuilder builder = new MessageChainBuilder();
            preHandleMsg(quoteMsgSource, isMe, username, builder);
            builder.add(text);
            MessageReceipt<?> receipt = group.sendMessage(builder.build());
            cachePool.execute(() -> CacheHolder.cache(receipt.getSource(), message));
        }
        return true;
    }

    @org.jetbrains.annotations.NotNull
    private String getUsername(Message message) {
        var from = Optional.ofNullable(message.getFrom());
        if (from.map(User::getUserName).filter("GroupAnonymousBot"::equalsIgnoreCase).isPresent()) {
            return message.getAuthorSignature();    //匿名用头衔作为前缀
        }
        return from.map(User::getFirstName).orElse("Null");
    }

    // qq 2 tg
    @Override
    public boolean handle(QQBotClient client, TelegramBotClient telegramBotClient, GroupAwareMessageEvent event) throws Exception {
        var  group   = event.getGroup();
        long groupId = group.getId();

        MessageChain messageChain = event.getMessage();

        var source = Optional.ofNullable(messageChain.get(OnlineMessageSource.Key));
        Optional<Integer> replyId = Optional.ofNullable(messageChain.get(QuoteReply.Key))
                .map(QuoteReply::getSource)
                .map(MessageSource::getIds)
                .map(ints -> ints[0])
                .map(CacheHolder.QQ_TG_MSG_ID_CACHE::get);

        long atAccount = -100;
        String content = formatContent(messageChain.stream().filter(m -> !(m instanceof Image)).map(msg -> {
            if (msg instanceof At) {
                if (((At) msg).getTarget() == atAccount) return "";
                return " " + ((At) msg).getDisplay(group) + " ";
            } else {
                return msg.contentToString();
            }
        }).collect(Collectors.joining()));
        if (content.startsWith("\n")) {
            content = content.substring(2);
        }

        var msg = tgMsgFormat.replace(QQ_PATTNER, String.valueOf(event.getSender().getId()))
                .replace(NAME_PATTNER, handleLongString(event.getSenderName(), 25).replace(" @", " "))
                .replace(MSG_PATTNER, content);
        String chatId = properties.getGroup().getQqTelegram().getOrDefault(groupId, properties.getGroup().getDefaultTelegram()).toString();

        if (StringUtils.isBlank(chatId) || chatId.equals("0")) return true;

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
                            cachePool.execute(() -> source.ifPresent(s -> CacheHolder.cache(s, messages.get(0))));
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
                            cachePool.execute(() -> source.ifPresent(s -> CacheHolder.cache(s, message)));
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
                            cachePool.execute(() -> source.ifPresent(s -> CacheHolder.cache(s, message)));
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
                                cachePool.execute(() -> source.ifPresent(s -> CacheHolder.cache(s, message)));
                            }
                            return true;
                        });
            } catch (TelegramApiException e) {
                log.error(e.getMessage(), e);
            }
        }
        log.debug("{}({}) - {}({}): {}", group.getName(), groupId, event.getSenderName(), event.getSender().getId(), content);
        return true;
    }

    @Override
    public int order() {
        return 100;
    }

    @org.jetbrains.annotations.NotNull
    private File getFile(TelegramBotClient client, String fileId, String fileUniqueId) {
        try {
            return client.execute(GetFile.builder().fileId(fileId).build());
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
        File file = new File();
        file.setFileId(fileId);
        file.setFileUniqueId(fileUniqueId);
        return file;
    }

    @NotNull
    private Optional<Image> getImage(TelegramBotClient client, Group group, String fileId, String fileUniqueId) throws TelegramApiException, IOException {
        File   file   = getFile(client, fileId, fileUniqueId);
        String suffix = getSuffix(file);
        var    image  = client.downloadFile(file);
        if (suffix.equalsIgnoreCase("webp")) {
            var png = webp2png(file.getFileId(), image);
            if (png != null) image = png;
        }
        try (var er = ExternalResource.create(image)) {
            return Optional.of(group.uploadImage(er));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    private java.io.File webp2png(String id, java.io.File webpFile) {
        var pngFile = new java.io.File("./cache/img/" + id + ".png");
        if (pngFile.exists()) return pngFile;

        pngFile.getParentFile().mkdirs();
        CompletableFuture<Process> future;
        try {
            future = Runtime.getRuntime().exec(String.format(webpCmdPattern, webpFile.getPath(), pngFile.getPath()).replace("\\", "\\\\")).onExit();
            if (future.get().exitValue() >= 0 || pngFile.exists()) return pngFile;
        } catch (IOException | ExecutionException | InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private boolean changeTgMsgFormat(String text) {
        String msgFormat;
        if (text.startsWith("/msg") && text.length() > 4 && StringUtils.isNotBlank(msgFormat = text.substring(5))) {
            this.tgMsgFormat = msgFormat;
            return true;
        }
        return false;
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

    @org.jetbrains.annotations.NotNull
    private String getSuffix(File file) {
        return file.getFilePath().substring(file.getFilePath().lastIndexOf('.') + 1);
    }

    private void preHandleMsg(Optional<OnlineMessageSource> quoteMsgSource, boolean isMaster, String username, MessageChainBuilder builder) {
        quoteMsgSource.map(QuoteReply::new).ifPresent(builder::add);
        if (!isMaster) builder.add(username + ": ");
    }
}
