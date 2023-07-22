package kurenai.imsyncbot.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@FieldNameConstants
public class FileCache {

    @Id
    @SnowFlakeGenerator
    String id;
    String fileId;
}
