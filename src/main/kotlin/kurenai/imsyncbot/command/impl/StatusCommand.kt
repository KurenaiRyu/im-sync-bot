package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import org.springframework.stereotype.Component

@Component
class StatusCommand : AbstractTelegramCommand() {

    override val command = "status"
    override val help: String = "bot运行状态"
    override val onlySupperAdmin = false

    override fun execute(update: Update, message: Message): String {
        val runtime = Runtime.getRuntime()
        val arr = arrayOf(runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory(), runtime.maxMemory(), runtime.freeMemory(), runtime.totalMemory())
            .map { it / 1024 / 1024 }
            .map { "${it}m" }
        return "总可用内存: ${arr[0]}/${arr[1]}\n" +
                "剩余可用分配内存: ${arr[2]}/${arr[3]}\n"
    }

}