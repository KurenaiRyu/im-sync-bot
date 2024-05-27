package kurenai.imsyncbot

import it.tdlight.Init
import kurenai.imsyncbot.bot.qq.QQHandler
import kurenai.imsyncbot.bot.qq.QQMessageHandler
import kurenai.imsyncbot.command.AbstractInlineCommand
import kurenai.imsyncbot.command.AbstractQQCommand
import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.utils.SnowFlake
import kurenai.imsyncbot.utils.getLogger
import kurenai.imsyncbot.utils.humanReadableByteCountBin
import org.reflections.Reflections
import org.slf4j.Logger
import java.io.File
import java.nio.file.Files
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask
import kotlin.io.path.fileSize

/**
 * @author Kurenai
 * @since 7/1/2022 09:31:04
 */

internal val log: Logger = getLogger()
internal val snowFlake = SnowFlake(1)
internal val reflections = Reflections("kurenai.imsyncbot")
internal val dfs: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

//internal val callbacks = reflections.getSubTypesOf(Callback::class.java).map { it.getConstructor().newInstance() }
internal val tgCommands = ArrayList<AbstractTelegramCommand>()
internal val qqCommands = ArrayList<AbstractQQCommand>()
internal val inlineCommands = HashMap<String, AbstractInlineCommand>()
internal val qqHandlers = ArrayList<QQHandler>()

internal lateinit var instants: MutableList<ImSyncBot>

//suspend fun main() {
//    instants = loadInstants()
//    configs.first()
//    commonInit()
//    instants.forEach { it.start() }
//}

suspend fun start() {
    Init.init() //td-lib
    ImSyncBot(configProperties)
    commonInit()
}

fun commonInit() {
    registerTgCommand()
    registerQQCommand()
    //TODO: 设置 inline 命令
//    registerInlineCommand()
    registerQQHandler()
    setUpTimer()
}

fun registerQQHandler() {
    reflections.getSubTypesOf(QQHandler::class.java)
        .filter { it != QQMessageHandler::class.java }
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach {
            qqHandlers.add(it)
            log.info("Registered qq handler:  ${it.handleName()}(${it::class.java.simpleName})")
        }
}

private fun registerTgCommand() {
    reflections.getSubTypesOf(AbstractTelegramCommand::class.java)
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach { command ->
            tgCommands.removeIf { it.name == command.name }
            tgCommands.add(command)
            log.info("Registered telegram command:  ${command.name}(${command::class.java.simpleName})")
        }
}

private fun registerQQCommand() {
    reflections.getSubTypesOf(AbstractQQCommand::class.java)
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach { command ->
            qqCommands.add(command)
            log.info("Registered qq command:  ${command.name}(${command::class.java.simpleName})")
        }
}

private fun registerInlineCommand() {
    reflections.getSubTypesOf(AbstractInlineCommand::class.java)
        .map { it.getDeclaredConstructor().newInstance() }
        .forEach { command ->
            if (command.command.isNotBlank()) {
                inlineCommands[command.command] = command
            }
            log.info("Registered inline command:  ${command.name}(${command::class.java.simpleName})")
        }
}

private val largeFileSize = 200 * 1024L
private val cacheAllowSize = 100 * 1024 * 1024L

private const val cachePath = "./cache"
private val clearCacheTimer = Timer("ClearCache", true)

private fun setUpTimer() {
    clearCacheTimer.scheduleAtFixedRate(timerTask {
        val cacheDir = File(cachePath)
        cacheDir.mkdirs()
        for (dirFile in cacheDir.listFiles()?.filter { it.isDirectory } ?: emptyList()) {
            try {
                if (!dirFile.exists()) {
                    log.warn("${dirFile.absolutePath} not exist!")
                    continue
                }

                val sizeOfDir = computeDirSize(dirFile)
                val filesToDelete = ArrayList<File>()
                if (sizeOfDir > cacheAllowSize) {
                    var deleteSize = 0L
                    val fileSet =
                        dirFile.listFiles()?.sortedByDescending { it.lastModified() }?.toMutableSet() ?: continue

                    // remove large file
                    fileSet.filter { f ->
                        f.toPath().fileSize() > largeFileSize
                    }.forEach {
                        deleteSize += Files.size(it.toPath())
                        fileSet.remove(it)
                        filesToDelete.add(it)
                    }

                    // remove until dir size less than allow cache size
                    for (file in fileSet) {
                        if (sizeOfDir - deleteSize > cacheAllowSize) {
                            deleteSize += Files.size(file.toPath())
                            filesToDelete.add(file)
                        } else
                            break
                    }
                    doDeleteCacheFile(filesToDelete)
                }
                log.info("Cache folder [${dirFile.name}] size: ${sizeOfDir.humanReadableByteCountBin()}")
            } catch (e: Exception) {
                log.error(e.message, e)
            }
        }
    }, 5000L, TimeUnit.HOURS.toMillis(1))
}

private fun computeDirSize(dirFile: File) = dirFile.listFiles()?.sumOf { Files.size(it.toPath()) } ?: 0L

private fun doDeleteCacheFile(filesToDelete: List<File>) {
    if (filesToDelete.isNotEmpty()) {
        //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
        log.info("Clearing cache files...")
        filesToDelete.forEach {
            log.debug("${it.name} deleted.")
            it.delete()
        } //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
        log.info("Clear ${filesToDelete.size} cache files.")
    }
}