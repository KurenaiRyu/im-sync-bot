package kurenai.imsyncbot.config

import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.ProxyProperties
import kurenai.imsyncbot.utils.HttpUtil
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AgeFileFilter
import org.apache.commons.io.filefilter.SizeFileFilter
import org.apache.commons.lang3.time.DateUtils
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultBotOptions
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*
import kotlin.concurrent.timerTask

@Component
class BotInitializer(
    val proxyProperties: ProxyProperties,
    val cacheService: CacheService
) : InitializingBean {

    private val log = KotlinLogging.logger {}
    private val largeFileSize = 1 * 1024 * 1024L
    private val largeDirSize = 500 * 1024 * 1024L

    private val cacheDir = File("./cache")
    private val clearCacheTimer = Timer("ClearCache", true)
    private val clearLargeCacheTimer = Timer("ClearLargeCache", true)

    override fun afterPropertiesSet() {
        configProxy()
        setUpTimer()
    }

    private fun configProxy() {
        HttpUtil.proxy = when (proxyProperties.type) {
            DefaultBotOptions.ProxyType.HTTP -> {
                Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyProperties.host, proxyProperties.port))
            }
            DefaultBotOptions.ProxyType.SOCKS4 -> {
                Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyProperties.host, proxyProperties.port))
            }
            DefaultBotOptions.ProxyType.SOCKS5 -> {
                Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyProperties.host, proxyProperties.port))
            }
            else -> {
                null
            }
        }
    }

    private fun setUpTimer() {
        clearCacheTimer.scheduleAtFixedRate(timerTask {
            val oldestAllowedFileDate = DateUtils.addDays(Date(), -1) //minus days from current date

            val filesToDelete = ArrayList<File>()
            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    filesToDelete.addAll(FileUtils.listFiles(file, AgeFileFilter(oldestAllowedFileDate), null))
                }
            }
            filesToDelete.removeIf { cacheService.imgExists(it.name) }
            doDeleteCacheFile(filesToDelete)
        }, 5000L, 30 * 60 * 60 * 1000L)

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

            // delete .part file
            val filesToDelete = ArrayList<File>()
            val oldestAllowedFileDate = DateUtils.addMinutes(Date(), -10)
            cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    filesToDelete.addAll(FileUtils.listFiles(file, AgeFileFilter(oldestAllowedFileDate), null).filter { it.name.endsWith(".part") })
                }
            }
        }, 1000L, 20 * 60 * 1000L)
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