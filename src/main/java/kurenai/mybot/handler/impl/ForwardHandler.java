package kurenai.mybot.handler.impl;

import io.ktor.util.collections.ConcurrentList;
import kurenai.mybot.CacheHolder;
import kurenai.mybot.QQBotClient;
import kurenai.mybot.TelegramBotClient;
import kurenai.mybot.handler.Handler;
import kurenai.mybot.handler.config.ForwardHandlerProperties;
import kurenai.mybot.utils.HttpUtil;
import kurenai.mybot.utils.RetryUtil;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.event.events.GroupAwareMessageEvent;
import net.mamoe.mirai.message.MessageReceipt;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.ExternalResource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaAnimation;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "bot.handler.forward", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ForwardHandlerProperties.class)
public class ForwardHandler implements Handler {

    private static final String ID_PATTNER      = "$id";
    private static final String NAME_PATTNER    = "$name";
    private static final String MSG_PATTNER     = "$msg";
    private static final String NEWLINE_PATTNER = "$newline";

    private final String                   webpCmdPattern;
    private final ExecutorService          uploadPool = Executors.newFixedThreadPool(10);
    private final ExecutorService          cachePool  = Executors.newFixedThreadPool(1);
    private final ForwardHandlerProperties properties;

    //TODO 最好将属性都提取出来，最少也要把第二层属性提取出来，不然每次判空
    private final Map<Long, String> bindingName;

    private String tgMsgFormat = "$name: $msg";
    private String qqMsgFormat = "$name: $msg";

    public ForwardHandler(ForwardHandlerProperties properties) {
        assert properties != null;
        this.properties = properties;
        String encoderPath;

        if (System.getProperties().getProperty("os.name").equalsIgnoreCase("Linux")) {
            encoderPath = new java.io.File("./bin/dwebp").getPath();
        } else {
            encoderPath = new java.io.File("./bin/dwebp.exe").getPath();
        }
        webpCmdPattern = encoderPath + " %s -o %s";

        bindingName = Optional.of(properties)
                .map(ForwardHandlerProperties::getMember)
                .map(ForwardHandlerProperties.Member::getBindingName)
                .orElse(Collections.emptyMap());

        Optional.ofNullable(properties.getTgMsgFormat()).filter(f -> f.contains(MSG_PATTNER)).ifPresent(f -> tgMsgFormat = f);
        Optional.ofNullable(properties.getQqMsgFormat()).filter(f -> f.contains(MSG_PATTNER)).ifPresent(f -> qqMsgFormat = f);
    }

    // tg 2 qq
    @Override
    public boolean handleMessage(TelegramBotClient client, QQBotClient qqClient, Update update, Message message) throws Exception {
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
        boolean isMaster = properties.getMasterOfQq() != null
                && Optional.ofNullable(message.getFrom()).map(User::getId).filter(properties.getMasterOfTg()::equals).isPresent();
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
            preHandleMsg(quoteMsgSource, isMaster, message.getFrom().getId(), username, Optional.ofNullable(message.getCaption())
                    .or(() -> Optional.ofNullable(message.getText()))
                    .orElse(""), builder);
            getImage(client, group, sticker.getFileId(), sticker.getFileUniqueId())
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
            preHandleMsg(quoteMsgSource, isMaster, message.getFrom().getId(), username, message.getCaption(), builder);
            builder.addAll(images);
            MessageReceipt<?> receipt = group.sendMessage(builder.build());
            cachePool.execute(() -> CacheHolder.cache(receipt.getSource(), message));
        } else if (message.hasText()) {
            var text = message.getText();
            //FIXME: 不知道为啥输出只有第一次会改变消息格式
            if (isMaster && handleChangeTgMsgFormatCmd(text)) {
                String demoContent = "demo msg.";
                var demoMsg = tgMsgFormat
                        .replace(NEWLINE_PATTNER, "\r\n")
                        .replace(NAME_PATTNER, "demo username")
                        .replace(ID_PATTNER, "123456789")
                        .replace(MSG_PATTNER, demoContent);
                client.execute(SendMessage.builder().chatId(chatId.toString()).text("changed msg format\r\ne.g.\r\n" + demoMsg).build());
                return false;
            }

            MessageChainBuilder builder = new MessageChainBuilder();
            preHandleMsg(quoteMsgSource, isMaster, message.getFrom().getId(), username, text, builder);
            MessageReceipt<?> receipt = group.sendMessage(builder.build());
            cachePool.execute(() -> CacheHolder.cache(receipt.getSource(), message));
        }
        return true;
    }

    @Override
    public boolean handleEditMessage(TelegramBotClient client, QQBotClient qqClient, Update update, Message message) throws Exception {
        Optional.ofNullable(message.getMessageId())
                .map(CacheHolder.TG_QQ_MSG_ID_CACHE::get)
                .map(CacheHolder.QQ_MSG_CACHE::get)
                .ifPresent(MessageSource::recall);
        return handleMessage(client, qqClient, update, message);
    }

    private String getUsername(Message message) {
        var from = Optional.ofNullable(message.getFrom());
        if (from.map(User::getUserName).filter("GroupAnonymousBot"::equalsIgnoreCase).isPresent()) {
            return Optional.ofNullable(message.getAuthorSignature()).orElse("");    //匿名用头衔作为前缀，空头衔将会不添加前缀
        }
        return from.map(User::getId).map(bindingName::get).or(() -> from.map(u -> {
            var flagA = StringUtils.isNotBlank(u.getFirstName());
            var flagB = StringUtils.isNotBlank(u.getLastName());
            var flagC = StringUtils.isNotBlank(u.getUserName());
            if (flagA && flagB) {
                return u.getFirstName() + " " + u.getLastName();
            } else if (flagA) {
                return u.getFirstName();
            } else if (flagB) {
                return u.getLastName();
            } else if (flagC) {
                return u.getUserName();
            } else {
                return "Null";
            }
        })).orElse("Null");
    }

    // qq 2 tg
    @Override
    public boolean handle(QQBotClient client, TelegramBotClient telegramBotClient, GroupAwareMessageEvent event) throws Exception {
        var group        = event.getGroup();
        var sender       = event.getSender();
        var senderName   = Optional.ofNullable(bindingName.get(sender.getId())).orElse(sender.getRemark().length() > 0 ? sender.getRemark() : event.getSenderName());
        var chatId       = properties.getGroup().getQqTelegram().getOrDefault(group.getId(), properties.getGroup().getDefaultTelegram()).toString();
        var messageChain = event.getMessage();

        boolean isMaster = client.getBot().getId() == sender.getId() || Optional.ofNullable(properties.getMasterOfQq()).filter(m -> m.equals(sender.getId())).isPresent();

        var content = messageChain.contentToString();
        if (isMaster && handleChangeQQMsgFormatCmd(content)) {
            String demoContent = "demo msg.";
            var demoMsg = qqMsgFormat
                    .replace(NEWLINE_PATTNER, "\r\n")
                    .replace(NAME_PATTNER, "demo username")
                    .replace(ID_PATTNER, "123456789")
                    .replace(MSG_PATTNER, demoContent);
            event.getSubject().sendMessage("changed msg format\r\ne.g.\r\n" + demoMsg);
            return false;
        }

        if (messageChain.contains(ForwardMessage.Key)) {
            return handleForwardMessage(client, telegramBotClient, messageChain.get(ForwardMessage.Key), group, chatId, senderName);
        }
        return handleMessage(client, telegramBotClient, messageChain, group, chatId, sender.getId(), senderName);
    }

    private boolean handleMessage(QQBotClient client, TelegramBotClient telegramBotClient, MessageChain messageChain, Group group, String chatId, long senderId, String senderName) throws Exception {
        var source = Optional.ofNullable(messageChain.get(OnlineMessageSource.Key));
        Optional<Integer> replyId = Optional.ofNullable(messageChain.get(QuoteReply.Key))
                .map(QuoteReply::getSource)
                .map(MessageSource::getIds)
                .map(ints -> ints[0])
                .map(CacheHolder.QQ_TG_MSG_ID_CACHE::get);

        AtomicLong atAccount = new AtomicLong(-100);
        String content = formatContent(messageChain.stream()
                .filter(m -> !(m instanceof Image))
                .map(msg -> getSingleContent(client, group, atAccount, msg))
                .collect(Collectors.joining()));

        if (content.startsWith("<?xml version='1.0'") || content.contains("\"app\":")) return true;

        if (content.startsWith("\n")) {
            content = content.substring(2);
        }

        var msg = tgMsgFormat
                .replace(NEWLINE_PATTNER, "\r\n")
                .replace(ID_PATTNER, String.valueOf(senderId))
                .replace(NAME_PATTNER, senderName.replace(" @", " "))
                .replace(MSG_PATTNER, content);


        if (StringUtils.isBlank(chatId) || chatId.equals("0")) return true;

        long count = messageChain.stream().filter(m -> m instanceof Image).count();
        if (count > 0) {
            if (count > 1) {
                List<InputMedia> medias = messageChain.stream().filter(m -> m instanceof Image)
                        .map(i -> {
                            Image  image = (Image) i;
                            String url   = Image.queryUrl(image);
                            try (var is = new ByteArrayInputStream(HttpUtil.download(url))) {
                                if (!image.getImageId().endsWith(".gif")) {
                                    var input = new InputMediaPhoto();
                                    input.setMedia(is, image.getImageId());
                                }
                            } catch (Exception e) {
                                log.error(e.getMessage(), e);
                            }
                            if (image.getImageId().endsWith(".gif")) {
                                return new InputMediaAnimation(url);
                            } else {
                                return new InputMediaPhoto(url);
                            }
                        })
                        .collect(Collectors.toList());
                InputMedia media = medias.get(0);
                media.setCaption(msg);
                var builder = SendMediaGroup.builder();
                replyId.ifPresent(builder::replyToMessageId);

                var sendMsg = builder
                        .medias(medias)
                        .chatId(chatId)
                        .build();
                try {
                    var result = RetryUtil.retry(3, () -> telegramBotClient.execute(sendMsg));
                    result.forEach(r -> cachePool.execute(() -> source.ifPresent(s -> CacheHolder.cache(s, r))));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                Image image = messageChain.get(Image.Key);
                if (image == null) return true;
                if (image.getImageId().endsWith(".gif")) {
                    var builder = SendAnimation.builder();
                    replyId.ifPresent(builder::replyToMessageId);
                    var sendMsg = builder
                            .caption(msg)
                            .chatId(chatId)
                            .animation(new InputFile(Image.queryUrl(image)))
                            .build();
                    try {
                        var result = RetryUtil.retry(3, () -> telegramBotClient.execute(sendMsg));
                        cachePool.execute(() -> source.ifPresent(s -> CacheHolder.cache(s, result)));
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                } else {

                    try (var is = new ByteArrayInputStream(HttpUtil.download(Image.queryUrl(image)))) {
                        var sendMsg = SendPhoto.builder()
                                .caption(msg)
                                .chatId(chatId)
                                .photo(new InputFile(is, image.getImageId()))
                                .build();
                        var result = RetryUtil.retry(3, () -> telegramBotClient.execute(sendMsg));
                        cachePool.execute(() -> source.ifPresent(s -> CacheHolder.cache(s, result)));
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }

                }
            }

        } else if (messageChain.contains(FileMessage.Key)) {
            var downloadInfo = messageChain.get(FileMessage.Key).toRemoteFile(group).getDownloadInfo();
            var url          = downloadInfo.getUrl();
            try (var is = new ByteArrayInputStream(HttpUtil.download(url))) {
                var filename = downloadInfo.getFilename().toLowerCase();
                if (filename.endsWith(".mkv") || filename.endsWith(".mp4"))
                    RetryUtil.retry(3, () -> telegramBotClient.execute(SendVideo.builder().video(new InputFile(is, downloadInfo.getFilename())).chatId(chatId).caption(msg).build()));
                else if (filename.endsWith(".bmp") || filename.endsWith(".jpeg") || filename.endsWith(".jpg") || filename.endsWith(".png"))
                    RetryUtil.retry(3, () -> telegramBotClient.execute(SendDocument.builder().document(new InputFile(is, downloadInfo.getFilename())).thumb(new InputFile(url)).chatId(chatId).caption(msg).build()));
                else
                    RetryUtil.retry(3, () -> telegramBotClient.execute(SendDocument.builder().document(new InputFile(is, downloadInfo.getFilename())).chatId(chatId).caption(msg).build()));
            } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException e) {
                log.error(e.getMessage(), e);
            }
        } else {
            var builder = SendMessage.builder();

            replyId.ifPresent(builder::replyToMessageId);
            var sendMsg = builder.chatId(chatId).text(msg).build();
            try {
                var result = RetryUtil.retry(3, () -> telegramBotClient.execute(sendMsg));
                cachePool.execute(() -> source.ifPresent(s -> CacheHolder.cache(s, result)));
            } catch (TelegramApiException e) {
                log.error(e.getMessage(), e);
            }
        }
        log.debug("{}({}) - {}({}): {}", group.getName(), group.getId(), senderName, senderId, content);
        return true;
    }

    private boolean handleForwardMessage(QQBotClient client, TelegramBotClient telegramBotClient, ForwardMessage msg, Group group, String chatId, String senderName) throws Exception {
        for (ForwardMessage.Node node : msg.getNodeList()) {
            try {
                handleMessage(client, telegramBotClient, node.getMessageChain(), group, chatId, node.getSenderId(), senderName + " forward from " + node.getSenderName());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return true;
    }

    private String getSingleContent(QQBotClient client, Group group, AtomicLong atAccount, SingleMessage msg) {
        if (msg instanceof At) {
            var at     = (At) msg;
            var target = at.getTarget();
            if (target == atAccount.get()) return "";
            else atAccount.set(target);
            String name = bindingName.get(target);
            if (name == null) {
                var friend = client.getBot().getFriend(target);
                if (friend != null) {
                    name = friend.getRemark().length() > 0 ? friend.getRemark() : friend.getNick();
                } else {
                    name = at.getDisplay(group);
                }
            }
            if (!name.startsWith("@")) {
                name = "@" + name;
            }
            return " " + name + " ";
        } else {
            return msg.contentToString();
        }
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
    private Optional<Image> getImage(TelegramBotClient client, Group group, String fileId, String fileUniqueId) throws TelegramApiException {
        File   file   = getFile(client, fileId, fileUniqueId);
        String suffix = getSuffix(file);
        var    image  = new java.io.File(file.getFilePath());
        if (!image.exists()) {
            image = client.downloadFile(file);
        }
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

    private boolean handleChangeQQMsgFormatCmd(String text) {
        String msgFormat;
        if (text.startsWith("/msg") && text.length() > 4 && StringUtils.isNotBlank(msgFormat = text.substring(5))) {
            this.qqMsgFormat = msgFormat;
            log.info("Change qq message format: {}", qqMsgFormat);
            return true;
        }
        return false;
    }

    private boolean handleChangeTgMsgFormatCmd(String text) {
        String msgFormat;
        if (text.startsWith("/msg") && text.length() > 4 && StringUtils.isNotBlank(msgFormat = text.substring(5))) {
            this.tgMsgFormat = msgFormat;
            log.info("Change tg message format: {}", tgMsgFormat);
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
        return getSuffix(file.getFilePath());
    }

    @org.jetbrains.annotations.NotNull
    private String getSuffix(String filename) {
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private void preHandleMsg(Optional<OnlineMessageSource> quoteMsgSource, boolean isMaster, long id, String username, String content, MessageChainBuilder builder) {

        quoteMsgSource.map(MessageSource::quote).ifPresent(builder::add);
        if (isMaster || StringUtils.isBlank(username))
            builder.add(Optional.ofNullable(content).orElse(""));
        else { //非空名称或是非主人则添加前缀
            var handledMsg = qqMsgFormat
                    .replace(NEWLINE_PATTNER, "\r\n")
                    .replace(NAME_PATTNER, username)
                    .replace(ID_PATTNER, String.valueOf(id))
                    .replace(MSG_PATTNER, Optional.ofNullable(content).orElse(""));
            builder.add(handledMsg);
        }
    }

}
