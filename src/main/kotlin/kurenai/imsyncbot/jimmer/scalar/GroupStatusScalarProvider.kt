package kurenai.imsyncbot.jimmer.scalar

import kurenai.imsyncbot.service.GroupStatus
import org.babyfish.jimmer.sql.runtime.AbstractScalarProvider

class GroupStatusScalarProvider :
    AbstractScalarProvider<Set<GroupStatus>, String>() {

    override fun toScalar(sqlValue: String): Set<GroupStatus> {
        return if (sqlValue.isEmpty()) {
            emptySet()
        } else {
            sqlValue.split(",").map { GroupStatus.valueOf(it.trim()) }.toSet()
        }
    }

    override fun toSql(scalarValue: Set<GroupStatus>): String {
        return scalarValue.joinToString(",") { it.toString() }
    }

}