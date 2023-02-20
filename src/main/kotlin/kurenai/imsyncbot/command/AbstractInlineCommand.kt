package kurenai.imsyncbot.command

import moe.kurenai.tdlight.model.inline.InlineQuery
import moe.kurenai.tdlight.model.message.Update

abstract class AbstractInlineCommand {

    open val name: String = this.javaClass.simpleName.replace("Command", "")
    abstract val command: String

    abstract suspend fun execute(update: Update, inlineQuery: InlineQuery, args: List<String>)

}