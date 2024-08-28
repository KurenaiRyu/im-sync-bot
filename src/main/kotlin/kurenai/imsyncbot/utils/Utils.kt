package kurenai.imsyncbot.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.ktor.client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.internal.toHexString
import org.reflections.Reflections
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Field
import java.nio.file.Path
import java.security.MessageDigest
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.zip.CRC32
import java.util.zip.CRC32C
import java.util.zip.Checksum
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.inputStream
import kotlin.math.abs

/**
 * @author Kurenai
 * @since 6/22/2022 18:51:10
 */

val reflections = Reflections("kurenai.imsyncbot")

fun getLogger(name: String = Thread.currentThread().stackTrace[2].className): Logger {
    return LoggerFactory.getLogger(name)
}

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

fun String?.suffix(): String {
    return this?.substring(this.lastIndexOf('.').plus(1)) ?: ""
}

fun Long.toLocalDateTime(): LocalDateTime {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
}

fun ByteArray.crc32c(): String {
    val checksum = CRC32C()
    checksum.update(this)
    return checksum.value.toHexString()
}

fun ByteArray.crc32(): String {
    val checksum = CRC32()
    checksum.update(this)
    return checksum.value.toHexString()
}

fun Path.crc32c(): String {
    return checksum(this, CRC32C())
}

fun Path.crc32(): String {
    return checksum(this, CRC32())
}

private fun checksum(path: Path, checksum: Checksum): String {
    path.inputStream().use { input ->
        val buff = ByteArray(DEFAULT_BUFFER_SIZE)
        while (input.read(buff) != -1) {
            checksum.update(buff)
        }
    }
    return checksum.value.toHexString()
}

fun ByteArray.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return md.digest(this).toHex()
}

fun Path.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    this.inputStream().use { input ->
        val buff = ByteArray(DEFAULT_BUFFER_SIZE)
        while (input.read(buff) != -1) {
            md.update(buff)
        }
    }
    return md.digest().toHex()
}

fun ByteArray.toHex(): String = HexFormat.of().formatHex(this)

fun String.parseHex(): ByteArray = HexFormat.of().parseHex(this)

suspend fun <R> withIO(block: suspend () -> R) = withContext(Dispatchers.IO) { block.invoke() }

fun <R> runIO(block: suspend () -> R) = CoroutineScope(Dispatchers.IO).launch { block.invoke() }

val httpClient = HttpClient()

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

fun setEnv(newenv: Properties) {
    val map = newenv.toMap() as MutableMap<String, String>
    try {
        val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
        val theEnvironmentField: Field = processEnvironmentClass.getDeclaredField("theEnvironment")
        theEnvironmentField.setAccessible(true)
        val env = theEnvironmentField.get(null) as MutableMap<String, String>
        env.putAll(map)
        val theCaseInsensitiveEnvironmentField: Field =
            processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment")
        theCaseInsensitiveEnvironmentField.setAccessible(true)
        val cienv = theCaseInsensitiveEnvironmentField.get(null) as MutableMap<String, String>
        cienv.putAll(map)
    } catch (e: NoSuchFieldException) {
        val classes = Collections::class.java.declaredClasses
        val env = System.getenv()
        for (cl in classes) {
            if ("java.util.Collections\$UnmodifiableMap" == cl.name) {
                val field: Field = cl.getDeclaredField("m")
                field.setAccessible(true)
                val map = field.get(env) as MutableMap<String, String>
                map.clear()
                map.putAll(map)
            }
        }
    }
}