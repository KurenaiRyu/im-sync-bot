package kurenai.imsyncbot.command

import moe.kurenai.tdlight.model.inline.InlineQuery
import moe.kurenai.tdlight.model.message.Update

abstract class InlineCommandHandler {

    abstract suspend fun execute(update: Update, inlineQuery: InlineQuery, args: List<String>)

}