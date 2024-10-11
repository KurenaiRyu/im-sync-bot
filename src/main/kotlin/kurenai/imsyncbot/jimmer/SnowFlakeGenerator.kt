package kurenai.imsyncbot.jimmer

import kurenai.imsyncbot.snowFlake
import org.babyfish.jimmer.sql.meta.UserIdGenerator

class SnowFlakeGenerator: UserIdGenerator<Long> {

    override fun generate(entityType: Class<*>): Long {
        return snowFlake.nextId()
    }
}