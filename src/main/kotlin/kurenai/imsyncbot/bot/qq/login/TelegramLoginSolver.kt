package kurenai.imsyncbot.bot.qq.login

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.bot.telegram.TelegramDisposableHandler
import kurenai.imsyncbot.utils.BotUtil
import kurenai.imsyncbot.utils.TelegramUtil.asFmtText
import kurenai.imsyncbot.utils.getLogger
import net.mamoe.mirai.Bot
import net.mamoe.mirai.auth.QRCodeLoginListener
import net.mamoe.mirai.utils.DeviceVerificationRequests
import net.mamoe.mirai.utils.DeviceVerificationResult
import net.mamoe.mirai.utils.LoginSolver
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.time.Duration.Companion.minutes

/**
 * @author Kurenai
 * @since 2023/4/17 17:29
 */

class TelegramLoginSolver(private val imSyncBot: ImSyncBot) : LoginSolver() {

    companion object {
        private val log = getLogger()
        private val loginSolverLock = Mutex()
    }

    private val telegram = imSyncBot.tg

    override fun createQRCodeLoginListener(bot: Bot): QRCodeLoginListener {

        return object : QRCodeLoginListener {

            override fun onFetchQRCode(bot: Bot, data: ByteArray) {
                runBlocking {
                    telegram.send {
                        val filename = "qrcode-${System.currentTimeMillis()}.png"
                        val path = Path.of(BotUtil.getImagePath(filename))
                        path.writeBytes(data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                        TdApi.SendMessage().apply {
                            this.chatId = imSyncBot.userConfig.masterTg
                            this.inputMessageContent = InputMessagePhoto().apply {
                                this.caption = "请在手机 QQ 使用账号 ${bot.id} 扫码".asFmtText()
                                this.photo = InputFileLocal(path.pathString)
                            }
                        }
                    }
                }
            }

            override fun onStateChanged(bot: Bot, state: QRCodeLoginListener.State) {
                runBlocking {
                    when (state) {
                        QRCodeLoginListener.State.WAITING_FOR_SCAN -> send("等待扫描二维码中")
                        QRCodeLoginListener.State.WAITING_FOR_CONFIRM -> send("扫描完成，请在手机 QQ 确认登录")
                        QRCodeLoginListener.State.CANCELLED -> send("已取消登录，将会重新获取二维码")
                        QRCodeLoginListener.State.TIMEOUT -> send("扫描超时，将会重新获取二维码")
                        QRCodeLoginListener.State.CONFIRMED -> send("已确认登录")
                        else -> {}
                    }
                }
            }

        }
    }

    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? = loginSolverLock.withLock {
        runCatching {
            telegram.send {
                val filename = "captcha-${System.currentTimeMillis()}.png"
                val path = Path.of(BotUtil.getImagePath(filename))
                path.writeBytes(data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                TdApi.SendMessage().apply {
                    this.chatId = imSyncBot.userConfig.masterTg
                    this.inputMessageContent = InputMessagePhoto().apply {
                        this.caption = "请输入验证码".asFmtText()
                        this.photo = InputFileLocal(path.pathString)
                    }
                }
            }
            getInput()
        }.onFailure {
            log.error("Telegram验证方式失败", it)
        }.getOrNull()
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? = loginSolverLock.withLock {
        runCatching {
            telegram.sendMessageText(
                "需要滑动验证码, 请按照以下链接的步骤完成滑动验证码, 然后输入获取到的 ticket\n$url",
                imSyncBot.userConfig.masterTg
            )
            getInput()
        }.onFailure {
            log.error("Telegram验证方式失败", it)
        }.getOrNull()
    }

    override suspend fun onSolveDeviceVerification(bot: Bot, requests: DeviceVerificationRequests): DeviceVerificationResult = loginSolverLock.withLock {
        runCatching {
            requests.sms?.let { sms ->
                val answer =
                    sendAndGet("一条短信验证码将发送到你的手机 (+${sms.countryCode}) ${sms.phoneNumber}. 运营商可能会收取正常短信费用, 是否继续? 输入 yes 继续, 输入其他终止并尝试其他验证方式.")
                if (answer.trim().equals("yes", true)) {
                    log.info("Attempting SMS verification.")
                    sms.requestSms()
                    return@runCatching sms.solved(sendAndGet("请输入验证码"))
                }
            }
            requests.fallback?.let { fallback ->
                solveFallback(fallback.url)
                return@runCatching fallback.solved()
            }
            error("User rejected SMS login while fallback login method not available.")
        }.onFailure {
            log.error("设备验证失败", it)
        }.getOrThrow()
    }

    private suspend fun solveFallback(url: String): String {
        return sendAndGet("当前登录环境不安全，服务器要求账户认证。请在 QQ 浏览器打开 $url 并完成验证后输入任意字符。")
    }

    private suspend fun sendAndGet(message: String): String {
        send(message)
        return getInput()
    }

    private suspend fun send(message: String) = telegram.sendMessageText(message, imSyncBot.userConfig.masterTg)

    private suspend fun getInput(): String {
        val deferred = CompletableDeferred<String>()

        val handler = TelegramDisposableHandler { _, msg ->
            val text = (msg.content as? MessageText)?.text?.text
            log.info("Telegram input: (${msg.id}) $text")
            if (msg.chatId == imSyncBot.configProperties.bot.masterOfTg && text?.isNotBlank() == true && text.length > 3) {
                deferred.complete(text)
                true
            } else {
                false
            }
        }
        telegram.disposableHandlers.add(handler)
        return runCatching {
            withTimeout(5.minutes) {
                deferred.await()
            }
        }.onFailure {
            telegram.disposableHandlers.remove(handler)
        }.getOrThrow()
    }


}