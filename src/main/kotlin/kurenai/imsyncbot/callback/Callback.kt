package kurenai.imsyncbot.callback

import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import mu.KotlinLogging

abstract class Callback {

    private val log = KotlinLogging.logger {}

    companion object {
        const val CONTINUE = 1
        const val END = 2

        const val WITHOUT_ANSWER = 1

        const val CONTINUE_WITHOUT_ANSWER = 11
        const val END_WITHOUT_ANSWER = 12
    }

    open val name: String = this.javaClass.simpleName
    open val method: String = ""

    open suspend fun handle(update: Update, message: Message): Int {
        var result = END
        try {
            result = handle0(update, message)
            return result % 10
        } finally {
            try {
                if (result % 100 / 10 != WITHOUT_ANSWER) {
//                    ContextHolder.telegramBotClient.send(AnswerCallbackQuery(update.callbackQuery.id))
                }
            } catch (e: Exception) {
                log.warn { e.message }
            }
        }
    }

    abstract suspend fun handle0(update: Update, message: Message): Int

    open fun match(update: Update): Boolean {
        return match(update.callbackQuery?.data ?: "").also {
            if (it) {
                log.debug { "Match ${this.name} callback" }
            }
        }
    }

    open fun match(text: String): Boolean {
        return text.substringBefore(" ") == method
    }

    fun getBody(update: Update): String {
        return update.callbackQuery?.data?.substringAfter(" ") ?: ""
    }

}