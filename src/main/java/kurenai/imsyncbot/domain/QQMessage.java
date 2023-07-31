package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

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
    Long id;
    Integer messageId;
    Long botId;
    Long objId;
    Long sender;
    Long target;
    QQMessageType type;
    @Column(name = "JSON_TXT")
    @Lob
    String json;
    Boolean handled;
    LocalDateTime msgTime;
    @Version
    @Column(columnDefinition = "default 0")
    Integer version;
}
