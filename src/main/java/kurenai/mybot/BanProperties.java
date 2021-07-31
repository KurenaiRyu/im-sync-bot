package kurenai.mybot;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "bot.ban")
@Data
public class BanProperties {
    private List<Long> group;
    private List<Long> member;
}
