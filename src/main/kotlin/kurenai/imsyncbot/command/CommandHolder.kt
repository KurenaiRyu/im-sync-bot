package kurenai.imsyncbot.command

import kurenai.imsyncbot.utils.reflections
import org.apache.logging.log4j.LogManager

/**
 * @author Kurenai
 * @since 6/22/2022 18:58:45
 */

object CommandHolder {

    private val log = LogManager.getLogger()

    val tgHandlers = ArrayList<AbstractTelegramCommand>()
    val qqHandlers = ArrayList<AbstractQQCommand>()
    val inlineCommands = HashMap<String, InlineCommandHandler>()

    fun init() {
        reflections.getSubTypesOf(AbstractTelegramCommand::class.java)
            .map { it.getDeclaredConstructor().newInstance() }
            .forEach(this::registerTgCommand)

        reflections.getSubTypesOf(AbstractQQCommand::class.java)
            .map { it.getDeclaredConstructor().newInstance() }
            .forEach(this::registerQQCommand)
    }

    private fun registerTgCommand(handler: AbstractTelegramCommand) {
        tgHandlers.removeIf { it.name == handler.name }
        tgHandlers.add(handler)
        log.info("Registered telegram command:  ${handler.name}(${handler::class.java.simpleName})")
    }

    fun registerQQCommand(handler: AbstractQQCommand) {
        qqHandlers.add(handler)
        log.info("Registered qq command:  ${handler.name}(${handler::class.java.simpleName})")
    }
}