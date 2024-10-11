package kurenai.imsyncbot.domain;

/**
 * @author Kurenai
 * @since 2023/7/22 19:36
 */

/*

@Entity
@Table(
        name = "QQ_MESSAGE", indexes = {
        @Index(
                name = "QQ_MESSAGE_uindex",
                columnList = "botId, targetId, messageId DESC",
                unique = true
        )}
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QQMessage {
    @Id
    @SnowFlakeGenerator
    private Long id;
    private Integer messageId;
    private Long botId;
    private Long fromId;
    private Long targetId;
    @Enumerated(EnumType.STRING)
    private MessageSourceKind type;
    @Enumerated(EnumType.STRING)
    @ColumnDefault("NORMAL")
    private MessageStatus status = MessageStatus.NORMAL;
    @Column(name = "JSON_TXT")
    @Lob
    private String json;
    private Boolean handled;
    private LocalDateTime time;
    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private Integer version;
}
*/
