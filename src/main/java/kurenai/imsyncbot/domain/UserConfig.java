package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import kurenai.imsyncbot.service.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.CollectionType;

import java.util.HashSet;
import java.util.Set;

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
    //TODO: AttributeConverter 是否在Hibernate reactive 生效
    private Set<UserStatus> status;
    @Version
    @Column(columnDefinition = "integer default 0")
    private Integer version;

}
