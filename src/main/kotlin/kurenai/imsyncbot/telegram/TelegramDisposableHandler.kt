package kurenai.imsyncbot.telegram

import kurenai.imsyncbot.ImSyncBot
import kurenai.imsyncbot.handler.Handler
import moe.kurenai.tdlight.model.message.Update
import java.time.LocalTime

/**
 * 一次性处理器
 *
 * 一般用于临时添加处理一次会话，在一般处理之前。
 * @author Kurenai
 * @since 2023/5/26 22:14
 */

fun interface TelegramDisposableHandler {

    val timeout: LocalTime
        get() = LocalTime.now()

    /**
     * Handle
     *
     * 一般不匹配直接返回 CONTINUE
     *
     * @param bot
     * @param update
     * @return
     */
    fun handle(bot: ImSyncBot, update: Update): Boolean

}