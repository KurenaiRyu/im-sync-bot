package kurenai.imsyncbot.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import kurenai.imsyncbot.service.GroupStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.GenericGenerator;

import java.util.HashSet;

/**
 * @author Kurenai
 * @since 2023/7/22 18:26
 */

@Entity
@Table(name = "GROUP_CONFIG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class GroupConfig {

    @Id
    @SnowFlakeGenerator
    private Long id;
    private Long qqGroupId;
    private String name;
    private Long telegramGroupId;
    private Long discordChannelId;
    private HashSet<GroupStatus> status;

}
