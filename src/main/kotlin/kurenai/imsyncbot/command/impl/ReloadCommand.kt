//package kurenai.imsyncbot.command.impl
//
//import it.tdlight.jni.TdApi
//import kurenai.imsyncbot.command.AbstractTelegramCommand
//import kurenai.imsyncbot.getBotOrThrow
//import moe.kurenai.tdlight.model.message.Message
//
//class ReloadCommand : AbstractTelegramCommand() {
//
//    override val command = "reload"
//    override val help: String = "重新加载config目录下的配置"
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String {
//        val bot = getBotOrThrow()
//        bot.groupConfig.reload()
//        bot.userConfig.reload()
//        return "已重新加载配置"
//    }
//}