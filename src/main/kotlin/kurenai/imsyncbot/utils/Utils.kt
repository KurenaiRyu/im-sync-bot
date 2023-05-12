package kurenai.imsyncbot.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kurenai.imsyncbot.qq.GroupMessageContext
import moe.kurenai.tdlight.model.message.Update
import org.reflections.Reflections
import java.io.File
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs

/**
 * @author Kurenai
 * @since 6/22/2022 18:51:10
 */

val reflections = Reflections("kurenai.imsyncbot")

/**
 * Creates a child scope of the receiver scope.
 */
fun CoroutineScope.childScope(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): CoroutineScope = this.coroutineContext.childScope(coroutineContext)

/**
 * Creates a child scope of the receiver context scope.
 */
fun CoroutineContext.childScope(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): CoroutineScope = CoroutineScope(this.childScopeContext(coroutineContext))

/**
 * Creates a child scope of the receiver context scope.
 */
fun CoroutineContext.childScopeContext(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
): CoroutineContext {
    val ctx = this + coroutineContext
    val job = ctx[Job] ?: return ctx + SupervisorJob()
    return ctx + SupervisorJob(job)
}

inline fun CoroutineScope.launchWithPermit(
    semaphore: Semaphore,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend () -> Unit,
): Job {
    return launch(coroutineContext) {
        semaphore.withPermit { block() }
    }
}

/**
 * 创建空文件（包括目录）
 */
fun File.createEmpty(): File {
    this.parentFile.mkdirs()
    if (!this.exists()) this.createNewFile()
    return this
}

/**
 * 创建父类目录
 */
fun File.parentMkdirs(): File {
    this.parentFile.mkdirs()
    return this
}

/**
 * SI (1 k = 1,000)
 */
fun Long.humanReadableByteCountSI(): String {
    var bytes = this
    if (-1000 < bytes && bytes < 1000) {
        return "$bytes B"
    }
    val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
    while (bytes <= -999950 || bytes >= 999950) {
        bytes /= 1000
        ci.next()
    }
    return String.format("%.1f %cB", bytes / 1000.0, ci.current())
}

/**
 * Binary (1 K = 1,024)
 */
fun Long.humanReadableByteCountBin(): String {
    val absB = if (this == Long.MIN_VALUE) Long.MAX_VALUE else abs(this)
    if (absB < 1024) {
        return "$this B"
    }
    var value = absB
    val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
    var i = 40
    while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
        value = value shr 10
        ci.next()
        i -= 10
    }
    value *= java.lang.Long.signum(this).toLong()
    return String.format("%.1f %ciB", value / 1024.0, ci.current())
}

fun Update.chatInfoString() = this.message?.chat?.let { "[${it.title ?: it.username}(${it.id})]" } ?: ""

fun GroupMessageContext.groupInfoString() = "[${this.group.name}(${this.group.id})]"

fun String?.suffix(): String {
    return this?.substring(this.lastIndexOf('.').plus(1)) ?: ""
}

val json = Json {
    encodeDefaults = true
    isLenient = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

val yamlMapper = ObjectMapper(
    YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
).registerModule(kotlinModule {
    withReflectionCacheSize(512)
    configure(KotlinFeature.NullToEmptyCollection, false)
    configure(KotlinFeature.NullToEmptyMap, false)
    configure(KotlinFeature.NullIsSameAsDefault, false)
    configure(KotlinFeature.SingletonSupport, false)
    configure(KotlinFeature.StrictNullChecks, false)
}).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)