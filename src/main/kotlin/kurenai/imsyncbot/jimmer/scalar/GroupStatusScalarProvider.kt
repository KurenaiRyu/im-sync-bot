package kurenai.imsyncbot.jimmer.scalar

import kurenai.imsyncbot.service.GroupStatus
import org.babyfish.jimmer.sql.runtime.AbstractScalarProvider

class GroupStatusScalarProvider :
    AbstractScalarProvider<MutableSet<GroupStatus>, String>() {

    override fun toScalar(sqlValue: String): MutableSet<GroupStatus> {
        return if (sqlValue.isEmpty()) {
            mutableSetOf()
        } else {
            sqlValue.split(",").map { GroupStatus.valueOf(it.trim()) }.toMutableSet()
        }
    }

    override fun toSql(scalarValue: MutableSet<GroupStatus>): String {
        return scalarValue.joinToString(",") { it.toString() }
    }

}