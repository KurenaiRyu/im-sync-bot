package kurenai.imsyncbot.qq.login

import jodd.net.MimeTypes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.ImSyncBot
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.UpdateType
import moe.kurenai.tdlight.request.chat.GetUpdates
import moe.kurenai.tdlight.request.message.SendMessage
import moe.kurenai.tdlight.request.message.SendPhoto
import moe.kurenai.tdlight.util.getLogger
import net.mamoe.mirai.Bot
import net.mamoe.mirai.auth.QRCodeLoginListener
import net.mamoe.mirai.utils.DeviceVerificationRequests
import net.mamoe.mirai.utils.DeviceVerificationResult
import net.mamoe.mirai.utils.LoginSolver
import net.mamoe.mirai.utils.currentTimeSeconds
import java.io.File
import javax.imageio.ImageIO

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
                    data.inputStream().use { input ->
                        telegram.client.send(SendPhoto(
                            imSyncBot.configProperties.bot.masterOfTg.toString(),
                            InputFile(input, "qrcode-${currentTimeSeconds()}.png")
                        ).apply {
                            caption = "请在手机 QQ 使用账号 ${bot.id} 扫码"
                        })
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
            data.inputStream().use { input ->
                val messageId = telegram.client.send(SendPhoto(imSyncBot.configProperties.bot.masterOfTg.toString(), InputFile(input, "captcha.jpg")).apply {
                    caption = "请输入验证码"
                }).messageId
                getInput(messageId!!)
            }
        }.onFailure {
            log.error("Telegram验证方式失败", it)
        }.getOrNull()
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? = loginSolverLock.withLock {
        runCatching {
            val messageId = telegram.client.send(
                SendMessage(
                    imSyncBot.configProperties.bot.masterOfTg.toString(),
                    "需要滑动验证码, 请按照以下链接的步骤完成滑动验证码, 然后输入获取到的 ticket\n$url"
                )
            ).messageId!!
            getInput(messageId)
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

    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String = loginSolverLock.withLock {
        solveFallback(url)
    }

    private suspend fun solveFallback(url: String): String {
        return sendAndGet("当前登录环境不安全，服务器要求账户认证。请在 QQ 浏览器打开 $url 并完成验证后输入任意字符。")
    }

    private suspend fun sendAndGet(message: String): String {
        val messageId = send(message).messageId!!
        return getInput(messageId)
    }

    private suspend fun send(message: String) = telegram.client.send(
        SendMessage(
            imSyncBot.configProperties.bot.masterOfTg.toString(),
            message
        )
    )

    private suspend fun getInput(messageId: Long): String {
        var message: Message? = null
        var offset = -1L
        while (message == null) {
            val update = telegram.client.send(GetUpdates(offset, 1, 10, listOf(UpdateType.MESSAGE))).firstOrNull() ?: continue
            val msg = update.message!!
            log.info("Telegram input: (${msg.messageId}) ${msg.text}")
            if (msg.chat.id == imSyncBot.configProperties.bot.masterOfTg && msg.messageId!! > messageId && msg.text?.isNotBlank() == true && msg.text!!.length > 3) {
                message = msg
            } else {
                offset = update.updateId + 1
            }
        }
        return message.text!!
    }


}