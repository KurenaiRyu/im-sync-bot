package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import kurenai.imsyncbot.service.GroupStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.util.HashSet;

/**
 * @author Kurenai
 * @since 2023/7/22 18:26
 */
/*

@Entity
@Table(name = "GROUP_CONFIG")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupConfig {

    @Id
    @SnowFlakeGenerator
    private Long id;
    private Long qqGroupId;
    private String name;
    private Long telegramGroupId;
    private Long discordChannelId;
    private HashSet<GroupStatus> status;
    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer version;

}
*/
