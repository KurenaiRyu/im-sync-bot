package kurenai.imsyncbot.discord

import mu.KotlinLogging
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.cache.CacheFlag
import okhttp3.OkHttpClient
import org.springframework.beans.factory.InitializingBean
import java.net.InetSocketAddress
import java.net.Proxy

class DiscordBotInitializer : InitializingBean {

    private val log = KotlinLogging.logger {}

    override fun afterPropertiesSet() {

        val builder: JDABuilder = JDABuilder.createDefault("ODA4MzU3ODg3NTI5MTg5Mzc3.YCFX8g.5ggoR7LKJSF12KAAJRTsoQU6X5k")

        val client = OkHttpClient().newBuilder().proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(7890))).build()
        builder.setHttpClient(client)

        // Disable parts of the cache
        builder.disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
        // Enable the bulk delete event
        builder.setBulkDeleteSplittingEnabled(false)
        // Disable compression (not recommended)
        builder.setCompression(Compression.ZLIB)
        // Set activity (like "playing Something")
        builder.setActivity(Activity.watching("TV"))

        val jda = builder.build()

        jda.addEventListener(DiscordListener())

        jda.awaitReady()

    }
}