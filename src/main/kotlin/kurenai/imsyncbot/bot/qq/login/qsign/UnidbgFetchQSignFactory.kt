package kurenai.imsyncbot.bot.qq.login.qsign

import kotlinx.coroutines.CoroutineScope
import net.mamoe.mirai.internal.spi.EncryptService
import net.mamoe.mirai.internal.spi.EncryptServiceContext
import net.mamoe.mirai.utils.BotConfiguration

/**
 * @author Kurenai
 * @since 2023/7/13 0:50
 */

class UnidbgFetchQSignFactory : EncryptService.Factory {

    override fun createForBot(context: EncryptServiceContext, serviceSubScope: CoroutineScope): EncryptService {
        return when (val protocol = context.extraArgs[EncryptServiceContext.KEY_BOT_PROTOCOL]) {
            BotConfiguration.MiraiProtocol.ANDROID_PHONE,
            BotConfiguration.MiraiProtocol.ANDROID_PAD -> {
                UnidbgFetchQSign(coroutineContext = serviceSubScope.coroutineContext)
            }

            BotConfiguration.MiraiProtocol.ANDROID_WATCH,
            BotConfiguration.MiraiProtocol.IPAD,
            BotConfiguration.MiraiProtocol.MACOS -> throw UnsupportedOperationException(protocol.name)
        }
    }

}