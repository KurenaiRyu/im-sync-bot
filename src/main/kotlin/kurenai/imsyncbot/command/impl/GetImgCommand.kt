package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.service.CacheService
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.media.InputFile
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.InputMediaDocument
import moe.kurenai.tdlight.request.message.SendDocument
import moe.kurenai.tdlight.request.message.SendMediaGroup
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl

class GetImgCommand : AbstractTelegramCommand() {

    override val command = "getimg"
    override val help: String = "获取消息中的媒体以文件发送"
    override val onlyGroupMessage = true
    override val onlyReply = true
    override val reply = true

    override suspend fun execute(update: Update, message: Message): String? {
        val replyMsg = message.replyToMessage ?: return "必须引用一条消息"
        val messageChain = CacheService.getQQByTg(replyMsg)
        if (messageChain != null) {
            val imgUrlList = messageChain.filterIsInstance<Image>().map { it.queryUrl() }
            if (imgUrlList.size == 1) {
                SendDocument(message.chatId, InputFile(imgUrlList.first())).send()
            } else if (imgUrlList.size in 1..10) {
                SendMediaGroup(message.chatId).apply {
                    media = imgUrlList.map { InputMediaDocument(InputFile(it)) }
                    replyToMessageId = message.messageId
                }.send()
            } else if (imgUrlList.size > 10) {
                imgUrlList.map { InputMediaDocument(InputFile(it)) }.windowed(10)
                SendMediaGroup(message.chatId).apply {
                    media = imgUrlList.map { InputMediaDocument(InputFile(it)) }
                    replyToMessageId = message.messageId
                }.send()
            }
        } else return "找不到该qq消息图片"
        return null
    }

}