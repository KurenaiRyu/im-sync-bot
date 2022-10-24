package kurenai.imsyncbot.callback.impl

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kurenai.imsyncbot.callback.Callback
import kurenai.imsyncbot.getBot
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.ParseMode
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.AnswerCallbackQuery
import moe.kurenai.tdlight.request.message.EditMessageText
import moe.kurenai.tdlight.util.MarkdownUtil.fm2md
import net.mamoe.mirai.contact.file.AbsoluteFolder
import org.apache.logging.log4j.LogManager

class GetFileUrlCallback : Callback() {

    companion object {
        private val log = LogManager.getLogger()
        const val METHOD = "GetFileUrl"
    }

    override val method: String = METHOD

    override suspend fun handle0(update: Update, message: Message): Int {
        val params = update.getParams()
        if (params.size == 2) {
            AnswerCallbackQuery(update.callbackQuery!!.id).apply {
                text = getBot()?.qq?.qqBot?.getGroup(params[0].toLong())?.files?.root?.files()?.mapNotNull {
                    (if (it.isFolder) {
                        it as AbsoluteFolder
                        it.files().firstOrNull { it.absolutePath == params[1] }
                    } else if (it.absolutePath == params[1]) {
                        it
                    } else null)?.getUrl()
                }?.first()?.let {
                    if (it.length > 180) {
                        val text = message.text?.replace("\n\nlink", "") ?: ""
                        EditMessageText(text.fm2md() + "\n\n[link]($it)").apply {
                            chatId = message.chatId
                            messageId = message.messageId
                            parseMode = ParseMode.MARKDOWN_V2
                            replyMarkup = message.replyMarkup
                        }.send()
                        "已刷新链接"
                    } else it
                } ?: kotlin.run {
                    "未找到该文件: ${params[1]}"
                }
                showAlert = true
            }.send()
        } else {
            log.warn("Params error: $params")
            AnswerCallbackQuery(update.callbackQuery!!.id).apply {
                text = "参数错误"
                showAlert = true
            }.send()
        }

        return END
    }
}