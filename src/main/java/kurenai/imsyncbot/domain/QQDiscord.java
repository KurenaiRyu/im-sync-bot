package kurenai.imsyncbot.domain;

/**
 * @author Kurenai
 * @since 2023/7/22 19:32
 */

import jakarta.persistence.*;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
        name = "QQ_DISCORD", indexes = {
        @Index(
                name = "QQ_DISCORD_CHANNEL_ID_DISCORD_MSG_ID_uindex",
                columnList = "discordChannelId, discordMsgId DESC",
                unique = true
        )}
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QQDiscord {

    @Id
    @SnowFlakeGenerator
    private Long id;
    private Long qqGrpId;
    private Integer qqMsgId;
    private Long discordChannelId;
    private Long discordMsgId;
    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer version;

}
