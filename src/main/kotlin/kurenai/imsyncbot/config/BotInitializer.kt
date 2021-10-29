package kurenai.imsyncbot.config

import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AgeFileFilter
import org.apache.commons.io.filefilter.SizeFileFilter
import org.apache.commons.lang3.time.DateUtils
import org.springframework.beans.factory.InitializingBean
import java.io.File
import java.util.*
import kotlin.concurrent.timerTask

class BotInitializer : InitializingBean {

    private val log = KotlinLogging.logger {}
    private val largeFileSize = 1 * 1024 * 1024L
    private val largeDirSize = 1 * 1024 * 1024 * 1024L

    override fun afterPropertiesSet() {
        val cacheDir = File("./cache")
        val clearCacheTimer = Timer("ClearCache", true)
        clearCacheTimer.scheduleAtFixedRate(timerTask {
            val oldestAllowedFileDate = DateUtils.addDays(Date(), -5) //minus days from current date

            val filesToDelete = ArrayList<File>()
            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    filesToDelete.addAll(FileUtils.listFiles(file, AgeFileFilter(oldestAllowedFileDate), null))
                }
            }
            doDeleteCacheFile(filesToDelete)
        }, 5000L, 12 * 60 * 60 * 1000L)

        val clearLargeCacheTimer = Timer("ClearLargeCache", true)
        clearLargeCacheTimer.scheduleAtFixedRate(timerTask {
            if (FileUtils.sizeOf(cacheDir) > largeDirSize) {
                val filesToDelete = ArrayList<File>()
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        filesToDelete.addAll(FileUtils.listFiles(file, SizeFileFilter(largeFileSize), null).filter { !it.name.endsWith(".part") })
                    }
                }
                doDeleteCacheFile(filesToDelete)
            }
        }, 1000L, 6 * 60 * 60 * 1000L)
    }

    private fun doDeleteCacheFile(filesToDelete: ArrayList<File>) {
        if (filesToDelete.isNotEmpty()) {
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            log.info { "Clearing cache files..." }
            filesToDelete.forEach {
                log.debug { "${it.name} deleted." }
                FileUtils.deleteQuietly(it)
            } //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            log.info { "Clear ${filesToDelete.size} cache files." }
        }
    }
}