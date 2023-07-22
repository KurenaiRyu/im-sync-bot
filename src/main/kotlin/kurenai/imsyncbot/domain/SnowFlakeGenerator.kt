package kurenai.imsyncbot.domain

import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.snowFlake
import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.generator.BeforeExecutionGenerator
import org.hibernate.generator.EventType
import org.hibernate.generator.EventTypeSets
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext
import java.lang.reflect.Member
import java.util.*

/**
 * @author Kurenai
 * @since 2023/7/22 18:29
 */

class SnowFlakeGenerator(
    val config: SnowFlakeGenerator,
    val idMember: Member,
    val creationContext: CustomIdGeneratorCreationContext,
) : BeforeExecutionGenerator {

    val memberType: MemberType

    init {
        val clazz = idMember.declaringClass
        memberType = if (Long::class.java.isAssignableFrom(clazz)) {
            MemberType.LONG
        } else if (String::class.java.isAssignableFrom(clazz)) {
            MemberType.STRING
        } else {
            throw BotException("Unsupported id type [" + clazz.name + "] for snow flake generator")
        }
    }

    override fun getEventTypes(): EnumSet<EventType> {
        return EventTypeSets.INSERT_ONLY
    }

    override fun generate(
        session: SharedSessionContractImplementor,
        owner: Any,
        currentValue: Any,
        eventType: EventType
    ): Any {
        val entityPersister = session.getEntityPersister(owner::class.qualifiedName, owner)
        return entityPersister.getIdentifier(owner, session) ?: run {
            when (memberType) {
                MemberType.LONG -> snowFlake.nextId()
                MemberType.STRING -> snowFlake.nextAlpha()
            }
        }
    }

    enum class MemberType {
        LONG, STRING,
    }


}