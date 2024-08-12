package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import kurenai.imsyncbot.service.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.ColumnDefault;

import java.util.HashSet;

/**
 * @author Kurenai
 * @since 2023/7/22 18:26
 */

@Entity
@Table(name = "USER_CONFIG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@NamedEntityGraph(
        name = "user",
        includeAllAttributes = true
)
public class UserConfig {

    @Id
    @SnowFlakeGenerator
    private Long id;
    private Long tg;
    private Long qq;
    private String bindingName;
    private HashSet<UserStatus> status;
    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer version;

}
