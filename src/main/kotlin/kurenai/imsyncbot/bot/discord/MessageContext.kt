package kurenai.imsyncbot.bot.discord

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.Webhook
import net.dv8tion.jda.api.utils.FileUpload
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.MessageChain

data class MessageContext(
    val messageChain: MessageChain,
    val webhook: Webhook,
    val senderName: String,
    val avatarUrl: String,
    val group: Group
) {
    val content = StringBuilder("")
    val files = mutableListOf<FileUpload>()
    val images = mutableListOf<FileUpload>()
    val embeds = mutableListOf<MessageEmbed>()
}