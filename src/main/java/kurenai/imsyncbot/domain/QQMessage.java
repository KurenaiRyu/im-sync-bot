package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.mamoe.mirai.message.data.MessageSourceKind;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * @author Kurenai
 * @since 2023/7/22 19:36
 */


@Entity
@Table(
        name = "QQ_MESSAGE", indexes = {
        @Index(
                name = "QQ_MESSAGE_MESSAGE_ID_BOT_ID_OBJ_ID_TYPE_uindex",
                columnList = "messageId DESC, botId, objId, type",
                unique = true
        )}
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QQMessage {
    @Id
    @SnowFlakeGenerator
    private Long id;
    private Integer messageId;
    private Long botId;
    private Long objId;
    private Long fromId;
    private Long targetId;
    @Enumerated(EnumType.STRING)
    private MessageSourceKind type;
    private Boolean handled;
    private LocalDateTime time;
    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer version;
}
