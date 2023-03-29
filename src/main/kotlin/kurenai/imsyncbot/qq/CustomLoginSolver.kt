package kurenai.imsyncbot.qq

import kurenai.imsyncbot.ImSyncBot
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.UpdateTypes
import moe.kurenai.tdlight.request.chat.GetUpdates
import moe.kurenai.tdlight.request.message.SendMessage
import moe.kurenai.tdlight.request.message.SendPhoto
import net.mamoe.mirai.Bot
import net.mamoe.mirai.utils.LoginSolver
import net.mamoe.mirai.utils.StandardCharImageLoginSolver

/**
 * @author Kurenai
 * @since 2023/3/16 5:20
 */

class CustomLoginSolver(private val imSyncBot: ImSyncBot) : LoginSolver() {

    companion object {
        private val default = StandardCharImageLoginSolver()
    }

    private val telegram = imSyncBot.tg

    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        val defaultResult = default.onSolvePicCaptcha(bot, data)
        return if (defaultResult.isNullOrEmpty()) {
            doSolvePicCaptcha(bot, data)
        } else {
            defaultResult
        }
    }

    private suspend fun doSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        return kotlin.runCatching {
            data.inputStream().use { input ->
                telegram.client.send(SendPhoto(imSyncBot.configProperties.bot.masterOfTg.toString(), InputFile(input, "captcha.jpg")).apply {
                    caption = "请输入验证码"
                })
                var message: Message? = null
                while (message == null) {
                    val msg = telegram.client.send(GetUpdates(0, 1, 10, listOf(UpdateTypes.MESSAGE))).first().message!!
                    if (msg.chat.id == imSyncBot.configProperties.bot.masterOfTg) {
                        message = msg
                    }
                }
                message.text
            }
        }.getOrNull()
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? {
        val defaultResult = default.onSolveSliderCaptcha(bot, url)
        return defaultResult.ifBlank {
            doSolveSliderCaptcha(bot, url)
        }
    }

    private suspend fun doSolveSliderCaptcha(bot: Bot, url: String): String? {
        return kotlin.runCatching {
            telegram.client.send(
                SendMessage(
                    imSyncBot.configProperties.bot.masterOfTg.toString(),
                    "需要滑动验证码, 请按照以下链接的步骤完成滑动验证码, 然后输入获取到的 ticket\n$url"
                )
            )
            var message: Message? = null
            while (message == null) {
                val msg = telegram.client.send(GetUpdates(0, 1, 10, listOf(UpdateTypes.MESSAGE))).first().message!!
                if (msg.chat.id == imSyncBot.configProperties.bot.masterOfTg) {
                    message = msg
                }
            }
            message.text
        }.getOrNull()
    }
}