package kurenai.imsyncbot.jimmer.scalar

import kurenai.imsyncbot.service.UserStatus
import org.babyfish.jimmer.sql.runtime.AbstractScalarProvider

class UserStatusScalarProvider :
    AbstractScalarProvider<Set<UserStatus>, String>() {

    override fun toScalar(sqlValue: String): Set<UserStatus> {
        return if (sqlValue.isEmpty()) {
            emptySet()
        } else {
            sqlValue.split(",").map { UserStatus.valueOf(it.trim()) }.toSet()
        }
    }

    override fun toSql(scalarValue: Set<UserStatus>): String {
        return scalarValue.joinToString(",") { it.toString() }
    }

}