package kurenai.imsyncbot.configuration.annotation

import kurenai.imsyncbot.domain.SnowFlakeGenerator
import org.hibernate.annotations.IdGeneratorType

/**
 * @author Kurenai
 * @since 2023/7/22 23:53
 */

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@IdGeneratorType(SnowFlakeGenerator::class)
annotation class SnowFlakeGenerator