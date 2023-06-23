package kurenai.imsyncbot.command

import it.tdlight.jni.TdApi
import it.tdlight.jni.TdApi.UpdateNewInlineQuery

abstract class AbstractInlineCommand {

    open val name: String = this.javaClass.simpleName.replace("Command", "")
    abstract val command: String

    abstract suspend fun execute(update: TdApi.Update, inlineQuery: UpdateNewInlineQuery, args: List<String>)

}