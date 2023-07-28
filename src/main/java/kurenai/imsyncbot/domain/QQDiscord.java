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
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.GenericGenerator;

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
@FieldNameConstants
public class QQDiscord {

    @Id
    @SnowFlakeGenerator
    Long id;
    Long qqGrpId;
    Integer qqMsgId;
    Long discordChannelId;
    Long discordMsgId;
    @Version
    @Column(columnDefinition = "default 0")
    Integer version;

}
