package kurenai.mybot.qq;

import lombok.Getter;
import lombok.Setter;
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "bot.qq")
public class QQBotProperties {

    private Long          account;
    private String        password;
    private MiraiProtocol protocol = MiraiProtocol.ANDROID_WATCH;
    private Filter        filter;

    @Getter
    @Setter
    public static class Filter {
        private List<Long> group;
        private List<Long> qq;
    }

}
