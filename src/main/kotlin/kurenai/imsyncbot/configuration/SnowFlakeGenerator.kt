package kurenai.imsyncbot.configuration

import kurenai.imsyncbot.configuration.annotation.SnowFlakeGenerator
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.snowFlake
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.generator.BeforeExecutionGenerator
import org.hibernate.generator.EventType
import org.hibernate.generator.EventTypeSets
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext
import java.lang.reflect.Field
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

    private val memberType: MemberType

    init {
        if (idMember is Field) {
            val type = idMember.type
            memberType =
                if (Long::class.java.isAssignableFrom(type) || java.lang.Long::class.java.isAssignableFrom(type)) {
                    MemberType.LONG
                } else if (String::class.java.isAssignableFrom(type)) {
                    MemberType.STRING
                } else {
                    throw BotException("Unsupported id type [" + type.name + "] for snow flake generator")
                }
        } else {
            throw BotException("Unsupported id [${idMember.declaringClass}.${idMember.name}] for snow flake generator")
        }
    }

    override fun getEventTypes(): EnumSet<EventType> {
        return EventTypeSets.INSERT_ONLY
    }

    override fun generate(
        session: SharedSessionContractImplementor,
        owner: Any,
        currentValue: Any?,
        eventType: EventType
    ): Any {
        return currentValue ?: run {
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