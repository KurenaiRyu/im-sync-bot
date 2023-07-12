package kurenai.imsyncbot.bot.qq.login.qsign

import com.tencent.mobileqq.channel.ChannelManager
import com.tencent.mobileqq.channel.SsoPacket
import com.tencent.mobileqq.qsec.qsecdandelionsdk.Dandelion
import com.tencent.mobileqq.sign.QQSecuritySign
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.bot.qq.login.qsign.QSign.BASE_PATH
import kurenai.imsyncbot.bot.qq.login.qsign.QSign.CONFIG
import kurenai.imsyncbot.utils.getLogger
import moe.fuqiuluo.unidbg.session.Session
import moe.fuqiuluo.unidbg.session.SessionManager
import net.mamoe.mirai.internal.spi.EncryptService
import net.mamoe.mirai.internal.spi.EncryptServiceContext
import net.mamoe.mirai.utils.MiraiInternalApi
import net.mamoe.mirai.utils.hexToBytes
import net.mamoe.mirai.utils.toUHexString
import kotlin.coroutines.CoroutineContext

/**
 * @author Kurenai
 * @since 2023/7/12 21:59
 */

class UnidbgFetchQSign(coroutineContext: CoroutineContext) : EncryptService, CoroutineScope {

    companion object {
        internal val log = getLogger()

        internal val CMD_WHITE_LIST = BASE_PATH.resolve("cmd.txt").readText().lines()
    }

    override val coroutineContext: CoroutineContext =
        coroutineContext + SupervisorJob(coroutineContext[Job]) + CoroutineExceptionHandler { context, exception ->
            when (exception) {
                is CancellationException -> {
                    // ...
                }

                else -> {
                    log.warn("with {}", context[CoroutineName], exception)
                }
            }
        }

    private var channel0: EncryptService.ChannelProxy? = null

    private val channel: EncryptService.ChannelProxy get() = channel0 ?: throw IllegalStateException("need initialize")

    private val token = java.util.concurrent.atomic.AtomicBoolean(false)

    @OptIn(MiraiInternalApi::class)
    override fun initialize(context: EncryptServiceContext) {
        val device = context.extraArgs[EncryptServiceContext.KEY_DEVICE_INFO]
        val qimei36 = context.extraArgs[EncryptServiceContext.KEY_QIMEI36]
        val channel = context.extraArgs[EncryptServiceContext.KEY_CHANNEL_PROXY]

        log.info("Bot(${context.id}) initialize by ${this::class.simpleName}")

        register(
            uin = context.id,
            androidId = device.androidId.decodeToString(),
            guid = device.guid.toUHexString(),
            qimei36 = qimei36
        )

        channel0 = channel

        log.info("Bot(${context.id}) initialize complete")
    }

    override fun encryptTlv(context: EncryptServiceContext, tlvType: Int, payload: ByteArray): ByteArray? {
        if (tlvType != 0x544) return null
        val command = context.extraArgs[EncryptServiceContext.KEY_COMMAND_STR]

        return customEnergy(uin = context.id, salt = payload, data = command)
    }

    override fun qSecurityGetSign(
        context: EncryptServiceContext,
        sequenceId: Int,
        commandName: String,
        payload: ByteArray
    ): EncryptService.SignResult? {
        if (commandName == "StatSvc.register") {
            if (!token.get() && token.compareAndSet(false, true)) {
                launch {
                    // requestToken(uin = context.id)
                }
            }
        }

        if (commandName !in CMD_WHITE_LIST) return null

        val (sign, requestCallback) = sign(uin = context.id, cmd = commandName, seq = sequenceId, buffer = payload)

        callback(uin = context.id, request = requestCallback)

        return EncryptService.SignResult(
            sign = sign.sign,
            token = sign.token,
            extra = sign.extra,
        )
    }

    private fun customEnergy(uin: Long, salt: ByteArray, data: String): ByteArray = runBlocking {

        val session = findSession(uin)

        val sign = session.mutex.withLock {
            Dandelion.energy(session.vm, data, salt)
        }

        log.debug("Bot({}) custom_energy {}, {}", uin, data, sign)

        sign
    }

    private fun register(uin: Long, androidId: String, guid: String, qimei36: String) {

        val envData = EnvData(
            uin,
            androidId,
            guid,
            qimei36,
            CONFIG.protocol.qua,
            CONFIG.protocol.version,
            CONFIG.protocol.code
        )

        SessionManager.register(envData)

        log.info("Bot(${uin}) register, $envData")
    }

    private fun sign(uin: Long, cmd: String, seq: Int, buffer: ByteArray, qimei36: String? = null) = runBlocking {
        SessionManager[uin] ?: error("Uin is not registered.")
        val session = findSession(uin)
        val vm = session.vm
        qimei36?.let { vm.global["qimei36"] = it }

        val requestCallback = arrayListOf<SsoPacket>()
        lateinit var o3did: String

        val sign = session.mutex.withLock {
            QQSecuritySign.getSign(vm, CONFIG.protocol.qua, cmd, buffer, seq, uin.toString()).value.also {
                o3did = vm.global["o3did"] as? String ?: ""
                val requiredPacket = vm.global["PACKET"] as ArrayList<SsoPacket>
                requestCallback.addAll(requiredPacket)
                requiredPacket.clear()
            }
        }

        log.debug("Bot({}) sign {}, {}", uin, cmd, sign)

        sign to requestCallback
    }

    private fun requestToken(uin: Long): List<SsoPacket> = runBlocking {
        val session = findSession(uin)

        val vm = session.vm

        if ("HAS_SUBMIT" !in vm.global) {
            error("QSign not initialized, unable to request_ Token, please submit the initialization package first.")
        }

        val list = arrayListOf<SsoPacket>()
        session.mutex.withLock {
            val lock = vm.global["mutex"] as Mutex
            lock.tryLock()
            QQSecuritySign.requestToken(vm)
            lock.withLock {
                val requiredPacket = vm.global["PACKET"] as ArrayList<SsoPacket>
                list.addAll(requiredPacket)
                requiredPacket.clear()
            }
        }

        log.info("Bot(${uin}) request_token, $list")

        list
    }

    private fun submit(uin: Long, cmd: String, callbackId: Int, buffer: ByteArray) = runBlocking {
        val session = findSession(uin)

        session.mutex.withLock {
            ChannelManager.onNativeReceive(session.vm, cmd, buffer, callbackId.toLong())

            session.vm.global["HAS_SUBMIT"] = true
        }

        log.debug("Bot(${uin}) submit $cmd")
    }

    private fun callback(uin: Long, request: List<SsoPacket>) {
        launch(CoroutineName("SendMessage")) {
            for (callback in request) {
                log.info("Bot(${uin}) sendMessage ${callback.cmd} ")
                val result = channel.sendMessage(
                    remark = "mobileqq.msf.security",
                    commandName = callback.cmd,
                    uin = 0,
                    data = callback.body.hexToBytes()
                )
                if (result == null) {
                    log.debug("{} ChannelResult is null", callback.cmd)
                    continue
                }

                submit(uin = uin, cmd = result.cmd, callbackId = callback.callbackId.toInt(), buffer = result.data)
            }
        }
    }

    private fun findSession(uin: Long): Session {
        return SessionManager[uin] ?: error("Uin is not registered.")
    }
}