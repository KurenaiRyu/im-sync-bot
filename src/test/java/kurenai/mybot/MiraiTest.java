//package kurenai.mybot;
//
//import net.mamoe.mirai.BotFactory;
//import net.mamoe.mirai.event.events.GroupMessageEvent;
//import net.mamoe.mirai.message.data.FileMessage;
//import net.mamoe.mirai.utils.BotConfiguration;
//import net.mamoe.mirai.utils.RemoteFile;
//
//import java.io.File;
//
//public class MiraiTest {
//
//    public static void main(String[] args) {
//        var bot = BotFactory.INSTANCE.newBot(1055316303, "kurenai@99", new BotConfiguration() {{
//            fileBasedDeviceInfo("device_1055.json"); // 使用 device.json 存储设备信息
//            setProtocol(MiraiProtocol.ANDROID_PHONE); // 切换协议
//        }});
//        bot.login();
//        bot.getEventChannel().subscribeAlways(GroupMessageEvent.class, event -> {
//            var test = event.getMessage().contentToString().contains("test");
//            if (event.getSender().getId() == 929956850 && test) {
//                var file = new File("D:\\Document\\Downloads\\Telegram Desktop\\{7D0F6A83-C1B0-1A92-2110-94F98C3512AF}.gif.mp4");
//                if (file.exists()) {
//                    event.
//                            getSubject()
//                            .getFilesRoot().uploadAndSend()
//                            .;
//                }
//            }
//        });
//    }
//}
