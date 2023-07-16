package kurenai.imsyncbot.config

import io.ktor.util.*
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import kurenai.imsyncbot.service.GroupStatus

/**
 * @author Kurenai
 * @since 2023/7/16 18:22
 */

@Converter(autoApply = true)
class StatusConverter : AttributeConverter<HashSet<GroupStatus>, String> {

    override fun convertToDatabaseColumn(attribute: HashSet<GroupStatus>?): String? {
        return attribute?.joinToString(",")
    }

    override fun convertToEntityAttribute(dbData: String?): HashSet<GroupStatus> {
        return dbData?.split(",")?.filter { it.isNotBlank() }?.map(GroupStatus::valueOf)?.toHashSet() ?: hashSetOf()
    }


}