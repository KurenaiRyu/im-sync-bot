package kurenai.imsyncbot.domain

import org.babyfish.jimmer.sql.EnumItem
import org.babyfish.jimmer.sql.EnumType

@EnumType(EnumType.Strategy.NAME)
enum class MessageStatus {
    @EnumItem(name = "NOR") NORMAL,
    @EnumItem(name = "DEL") DELETED,
    @EnumItem(name = "REV") REVISED
}