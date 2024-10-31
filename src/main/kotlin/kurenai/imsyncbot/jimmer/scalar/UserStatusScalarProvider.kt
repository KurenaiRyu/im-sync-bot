package kurenai.imsyncbot.jimmer.scalar

import kurenai.imsyncbot.service.UserStatus
import org.babyfish.jimmer.sql.runtime.AbstractScalarProvider

class UserStatusScalarProvider :
    AbstractScalarProvider<MutableSet<UserStatus>, String>() {

    override fun toScalar(sqlValue: String): MutableSet<UserStatus> {
        return if (sqlValue.isEmpty()) {
            mutableSetOf()
        } else {
            sqlValue.split(",").map { UserStatus.valueOf(it.trim()) }.toMutableSet()
        }
    }

    override fun toSql(scalarValue: MutableSet<UserStatus>): String {
        return scalarValue.joinToString(",") { it.toString() }
    }

}