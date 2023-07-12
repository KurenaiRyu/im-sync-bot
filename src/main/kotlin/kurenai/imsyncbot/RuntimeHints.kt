package kurenai.imsyncbot

import com.fasterxml.jackson.databind.cfg.ConstructorDetector
import dev.kord.common.entity.ApplicationCommandOptionType
import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.KeyboardButton
import it.tdlight.tdnative.NativeClient
import it.tdlight.util.NativeLibraryLoader
import nl.adaptivity.xmlutil.core.impl.multiplatform.name
import org.springframework.aot.hint.*
import org.springframework.aot.hint.RuntimeHints
import java.util.Collections
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType

/**
 * @author Kurenai
 * @since 2023/7/9 23:14
 */

class RuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.reflection().registerTypeIfPresent(
            classLoader,
            "com.github.benmanes.caffeine.cache.PSWMW",
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
        )
        hints.reflection().registerTypeIfPresent(
            classLoader,
            "com.github.benmanes.caffeine.cache.SSMSW",
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
        )
        hints.reflection().registerTypeIfPresent(
            classLoader,
            "com.github.benmanes.caffeine.cache.SSMS",
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
        )
        hints.reflection().registerTypeIfPresent(
            classLoader,
            "com.github.benmanes.caffeine.cache.SS",
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
        )
        hints.reflection().registerType(
            ConstructorDetector::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.DECLARED_CLASSES
        )
        hints.reflection().registerType(
            AtomicLongFieldUpdater::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.DECLARED_CLASSES
        )
        AtomicLongFieldUpdater::class.java.declaredClasses.forEach {
            hints.reflection().registerTypeIfPresent(
                classLoader,
                it.name,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.DECLARED_CLASSES
            )
        }

        hints.jni().registerType(
            java.lang.Boolean::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            java.lang.Integer::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            java.lang.Long::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            java.lang.Double::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            java.lang.String::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            java.util.Arrays::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            Array::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            IntArray::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            LongArray::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            DoubleArray::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            BooleanArray::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        TdApi::class.nestedClasses.forEach { clazz ->
            if (clazz.isSubclassOf(TdApi.Function::class).not()) {
                hints.jni().registerTypeIfPresent(classLoader, "${clazz.name}[]")
            }
//            clazz.java.declaredMethods.forEach {
//                hints.jni().registerMethod(it, ExecutableMode.INVOKE)
//            }
//            clazz.java.fields.forEach {
//                hints.jni().registerField(it)
//            }
            hints.reflection().registerType(
                clazz.java,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS
            )
            hints.jni().registerType(
                clazz.java,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS
            )
        }
        hints.jni().registerType(
            TdApi::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.DECLARED_CLASSES,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.jni().registerType(
            NativeClient::class.java,
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.DECLARED_CLASSES,
            MemberCategory.INVOKE_DECLARED_METHODS
        )
        hints.resources().registerPattern("META-INF/tdlightjni/*")
    }
}