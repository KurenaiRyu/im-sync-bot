package kurenai.imsyncbot.callback

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.telegram.send
import moe.kurenai.tdlight.model.message.Message
import moe.kurenai.tdlight.model.message.Update
import moe.kurenai.tdlight.request.message.SendMessage
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

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

    open fun handle(update: Update, message: Message): Int {
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

    abstract fun handle0(update: Update, message: Message): Int

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

    fun waitForMsg(update: Update, message: Message): Update? {
        val client = ContextHolder.telegramBot
        var lock = client.nextMsgLock[message.chat.id]
        if (lock == null) {
            lock = Object()
            client.nextMsgLock.putIfAbsent(message.chat.id, lock)?.let { lock = it }
        }
        synchronized(lock!!) {
            lock!!.wait(TimeUnit.SECONDS.toMillis(30))
        }
        return client.nextMsgUpdate.remove(message.chat.id) ?: run {
            SendMessage(message.chatId.toString(), "等待消息超时").send()
            null
        }
    }

}