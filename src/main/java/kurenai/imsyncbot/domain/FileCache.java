package kurenai.imsyncbot.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

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
    private String id;
    private String fileId;
    private String fileType;
    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer version;
}
