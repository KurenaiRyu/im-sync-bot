package kurenai.imsyncbot.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.reflections.Reflections
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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