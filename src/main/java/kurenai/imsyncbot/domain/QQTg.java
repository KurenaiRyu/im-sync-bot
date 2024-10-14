package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/**
 * @author Kurenai
 * @since 2023/7/22 19:40
 */
/*

@Entity
@Table(
        name = "QQ_TG", indexes = {
        @Index(name = "QQ_TG_GRP_ID_TG_MSG_ID_uindex", columnList = "tgGrpId, tgMsgId DESC", unique = true)
}
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QQTg {

    @Id
    @SnowFlakeGenerator
    private Long id;
    private Long qqId;
    private Integer qqMsgId;
    private Long tgGrpId;
    private Long tgMsgId;
    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer version;
}
*/
