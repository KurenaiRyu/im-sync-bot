package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.GenericGenerator;

/**
 * @author Kurenai
 * @since 2023/7/22 19:30
 */


@Entity
@Table(name = "FILE_CACHE")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileCache {

    @Id
    @SnowFlakeGenerator
    String id;
    String fileId;
    @Version
    @Column(columnDefinition = "default 0")
    Integer version;
}
