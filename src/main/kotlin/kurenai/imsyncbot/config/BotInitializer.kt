package kurenai.imsyncbot.config

import io.github.kurenairyu.cache.Cache
import kurenai.imsyncbot.humanReadableByteCountBin
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.ProxyProperties
import kurenai.imsyncbot.telegram.TelegramBotProperties
import kurenai.imsyncbot.utils.HttpUtil
import mu.KotlinLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.AgeFileFilter
import org.apache.commons.io.filefilter.SizeFileFilter
import org.apache.commons.lang3.time.DateUtils
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

@Component
class BotInitializer(
    private val tgProperties: TelegramBotProperties,
    private val proxyProperties: ProxyProperties,
    private val cacheService: CacheService,
    private val cache: Cache,
) : InitializingBean {

    private val log = KotlinLogging.logger {}
    private val largeFileSize = 200 * 1024L
    private val largeDirSize = 100 * 1024 * 1024L

    private val cacheDirs = listOf("./cache/img", "./cache/doc", "./cache/file")
    private val clearCacheTimer = Timer("ClearCache", true)

    override fun afterPropertiesSet() {
        checkRedisCodec()
        configProxy()
        configImgBaseUrl()
        setUpTimer()
    }

    private fun configProxy() {
        if (proxyProperties.type != Proxy.Type.DIRECT) {
            HttpUtil.PROXY = Proxy(proxyProperties.type, InetSocketAddress(proxyProperties.host, proxyProperties.port))
        }
    }

    private fun configImgBaseUrl() {
        tgProperties.imgBaseUrl?.isNotBlank()?.let {
            HttpUtil.IMG_BASE_URL = tgProperties.imgBaseUrl
        }

    }

    private fun setUpTimer() {
        clearCacheTimer.scheduleAtFixedRate(timerTask {
            for (cacheDir in cacheDirs) {
                try {
                    val dir = File(cacheDir)
                    val sizeOfDir = FileUtils.sizeOfDirectory(dir)
                    log.info { "Cache folder [${dir.name}] size: ${sizeOfDir.humanReadableByteCountBin()}" }
                    val filesToDelete = ArrayList<File>()
                    val files = cacheService.getNotExistFiles()?.map { File(it.value) }
                    if (files?.isNotEmpty() == true) filesToDelete.addAll(filesToDelete)

                    val oldestAllowedFileDate = DateUtils.addMinutes(Date(), -10)
                    if (sizeOfDir > largeDirSize) {
                        filesToDelete.addAll(
                            FileUtils.listFiles(
                                dir,
                                SizeFileFilter(largeFileSize),
                                null
                            ) as Collection<File>
                        )
                        filesToDelete.addAll(
                            FileUtils.listFiles(
                                dir,
                                AgeFileFilter(oldestAllowedFileDate),
                                null
                            ) as Collection<File>
                        )
                    }
                    doDeleteCacheFile(filesToDelete)
                } catch (e: Exception) {
                    log.error(e.message, e)
                }
            }
        }, 5000L, TimeUnit.MINUTES.toMillis(10))
    }

    private fun doDeleteCacheFile(filesToDelete: List<File>) {
        if (filesToDelete.isNotEmpty()) {
            //if deleting subdirs, replace null above with TrueFileFilter.INSTANCE
            log.info { "Clearing cache files..." }
            filesToDelete.forEach {
                log.debug { "${it.name} deleted." }
                it.delete()
            } //I don't want an exception if a file is not deleted. Otherwise use filesToDelete.next().delete() in a try/catch
            log.info { "Clear ${filesToDelete.size} cache files." }
        }
    }

    private fun checkRedisCodec() {
        val serializeType: String? = cache.get("SERIALIZE_TYPE", tgProperties.token.substringBefore(":"))
        if ("json" != serializeType?.lowercase()) {
            cache.clearAll()
            cache.put("SERIALIZE_TYPE", tgProperties.token.substringBefore(":"), "json")
        }
    }
}