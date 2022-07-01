package kurenai.imsyncbot.command.impl

import kurenai.imsyncbot.command.AbstractTelegramCommand
import kurenai.imsyncbot.service.CacheService
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import java.text.NumberFormat

class StatusCommand : AbstractTelegramCommand() {

    override val command = "status"
    override val help: String = "bot运行状态"
    override val onlySupperAdmin = false

    override suspend fun execute(update: Update, message: Message): String {
        val runtime = Runtime.getRuntime()
        val arr = arrayOf(
            runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory(),
            runtime.maxMemory(),
            runtime.freeMemory(),
            runtime.totalMemory()
        )
            .map { it / 1024 / 1024 }
            .map { "${it}m" }
        val total = CacheService.total.get()
        val hit = CacheService.hit.get()
        val formatter = NumberFormat.getPercentInstance()
        return """
            总可用内存: ${arr[0]}/${arr[1]}
            剩余可用分配内存: ${arr[2]}/${arr[3]}
            
            缓存命中率: $hit / $total (${formatter.format(hit / total.toFloat())})
             """.trimIndent()
    }

}