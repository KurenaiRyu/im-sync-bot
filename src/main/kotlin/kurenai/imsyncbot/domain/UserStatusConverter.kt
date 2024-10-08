package kurenai.imsyncbot.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import kurenai.imsyncbot.service.UserStatus

/**
 * @author Kurenai
 * @since 2023/7/16 18:22
 */

@Converter(autoApply = true)
class UserStatusConverter : AttributeConverter<HashSet<UserStatus>, String> {

    override fun convertToDatabaseColumn(attribute: HashSet<UserStatus>?): String? {
        return attribute?.joinToString(",")
    }

    override fun convertToEntityAttribute(dbData: String?): HashSet<UserStatus> {
        return dbData?.split(",")?.filter { it.isNotBlank() }?.map(UserStatus::valueOf)?.toHashSet() ?: hashSetOf()
    }

}