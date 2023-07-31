package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import kurenai.imsyncbot.service.UserStatus;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

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
public class UserConfig {

    @Id
    @SnowFlakeGenerator
    private Long id;
    private Long tg;
    private Long qq;
    private String bindingName;
    private HashSet<UserStatus> status;
    @Version
    @Column(columnDefinition = "integer default 0")
    Integer version;

}
