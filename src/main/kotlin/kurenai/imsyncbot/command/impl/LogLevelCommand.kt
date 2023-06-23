//package kurenai.imsyncbot.command.impl
//
//import it.tdlight.jni.TdApi
//import kurenai.imsyncbot.command.AbstractTelegramCommand
//import moe.kurenai.tdlight.model.message.Message
//import org.apache.logging.log4j.Level
//import org.apache.logging.log4j.LogManager
//import org.apache.logging.log4j.core.config.Configurator
//
///**
// * @author Kurenai
// * @since 2023/3/2 7:53
// */
//
//class LogLevelCommand : AbstractTelegramCommand() {
//
//    private val log = LogManager.getLogger()
//    override val help: String = "设置日志等级"
//    override val command: String = "loglevel"
//
//    override val onlyUserMessage = true
//
//    override suspend fun execute(message: Message, sender: TdApi.MessageSenderUser): String? {
//        val params = message.text?.param()?.split(" ") ?: return null
//        if (params.size != 2) return null
//
//        val packageName = params[0]
//        val level = params[1].uppercase()
//        Configurator.setLevel(packageName, Level.valueOf(level))
//        val message = "Set [$packageName] log level to $level"
//        log.info(message)
//        return message
//    }
//
//
//}