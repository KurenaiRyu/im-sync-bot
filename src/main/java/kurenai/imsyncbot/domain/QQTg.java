package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * @author Kurenai
 * @since 2023/7/22 19:40
 */

@Entity
@Table(
        name = "QQ_TG", indexes = {
        @Index(name = "QQ_TG_GRP_ID_TG_MSG_ID_uindex", columnList = "tgGrpId, tgMsgId DESC", unique = true)
}
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class QQTg {

    @Id
    @SnowFlakeGenerator
    Long id;
    Long qqId;
    Integer qqMsgId;
    Long tgGrpId;
    Long tgMsgId;
    @Version
    @Column(columnDefinition = "default 0")
    Integer version;
}
