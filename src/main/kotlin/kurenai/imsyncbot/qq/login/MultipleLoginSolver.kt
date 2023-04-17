package kurenai.imsyncbot.qq.login

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.exception.BotException
import kurenai.imsyncbot.log
import net.mamoe.mirai.Bot
import net.mamoe.mirai.auth.QRCodeLoginListener
import net.mamoe.mirai.utils.DeviceVerificationRequests
import net.mamoe.mirai.utils.DeviceVerificationResult
import net.mamoe.mirai.utils.LoginSolver
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.StandardCharImageLoginSolver
import net.mamoe.mirai.utils.currentTimeSeconds
import net.mamoe.mirai.utils.info
import net.mamoe.mirai.utils.warning
import java.io.File
import javax.imageio.ImageIO

/**
 * @author Kurenai
 * @since 2023/3/16 5:20
 */


class MultipleLoginSolver(private val imSyncBot: ImSyncBot) : LoginSolver() {

    companion object {
        private val default = StandardCharImageLoginSolver()
        private val loginSolverLock = Mutex()
    }

    private val solvers = mapOf(
        "默认" to default,
        "Telegram" to TelegramLoginSolver(imSyncBot)
    )

    private val sliderCaptchaSupportedSolvers = solvers.filter { (_, solver) -> solver.isSliderCaptchaSupported }

    override val isSliderCaptchaSupported = sliderCaptchaSupportedSolvers.isNotEmpty()

    override fun createQRCodeLoginListener(bot: Bot): QRCodeLoginListener {
        log.info("输入任意字符以确认控制台输入正常")
        return readlnOrNull()?.takeUnless { it.isBlank() }?.let {
            default.createQRCodeLoginListener(bot)
        } ?: run {
            solvers["Telegram"]!!.createQRCodeLoginListener(bot)
        }
    }

    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? = loginSolverLock.withLock {
        solvers.firstNotNullOfOrNull { (name, solver) ->
            kotlin.runCatching {
                solver.onSolvePicCaptcha(bot, data)
            }.onFailure {
                log.error("${name}图片验证失败", it)
            }.getOrNull()?.takeUnless { it.isBlank() }
        }
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? = loginSolverLock.withLock {
        sliderCaptchaSupportedSolvers
            .firstNotNullOfOrNull { (name, solver) ->
                kotlin.runCatching {
                    solver.onSolveSliderCaptcha(bot, url)
                }.onFailure {
                    log.error("${name}滑块验证失败", it)
                }.getOrNull()?.takeUnless { it.isBlank() }
            }
    }

    override suspend fun onSolveDeviceVerification(bot: Bot, requests: DeviceVerificationRequests): DeviceVerificationResult = loginSolverLock.withLock {
        solvers.firstNotNullOfOrNull { (name, solver) ->
            kotlin.runCatching {
                solver.onSolveDeviceVerification(bot, requests)
            }.onFailure {
                log.error("${name}设备验证失败", it)
            }.getOrNull()
        } ?: throw BotException("全部设备验证方式失败")
    }
}