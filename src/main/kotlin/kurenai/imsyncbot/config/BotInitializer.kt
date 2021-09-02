package kurenai.imsyncbot.config

import kurenai.imsyncbot.BotConfigConstant
import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.repository.BotConfigRepository
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AgeFileFilter
import org.apache.commons.lang3.time.DateUtils
import org.springframework.beans.factory.InitializingBean
import java.io.File
import java.util.*
import kotlin.concurrent.timerTask

class BotInitializer(
    private val botConfigRepository: BotConfigRepository,
) : InitializingBean {

    private val log = KotlinLogging.logger {}

    override fun afterPropertiesSet() {
        if (botConfigRepository.existsById(BotConfigConstant.MASTER_CHAT_ID)) {
            botConfigRepository.getById(BotConfigConstant.MASTER_CHAT_ID).value.takeIf { it.isNotBlank() }?.let {
                ContextHolder.masterChatId = it.toLong()
            }
        }

        val clearCacheTimer = Timer("clear cache file", true)
        clearCacheTimer.scheduleAtFixedRate(timerTask {
            val oldestAllowedFileDate = DateUtils.addDays(Date(), -3) //minus days from current date
            val cacheDir = File("./cache")

            val filesToDelete = ArrayList<File>()
            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    filesToDelete.addAll(FileUtils.listFiles(file, AgeFileFilter(oldestAllowedFileDate), null))
                }
            }

            if (filesToDelete.isNotEmpty()) {
                //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
                log.debug { "Clearing cache files..." }
                filesToDelete.forEach {
                    log.debug { "${it.name} deleted." }
                    FileUtils.deleteQuietly(it)
                } //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
                log.debug { "Clear ${filesToDelete.size} cache files." }
            }
        }, 5000L, 24 * 60 * 60 * 1000L)
    }
}