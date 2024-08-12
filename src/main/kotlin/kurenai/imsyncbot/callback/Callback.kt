//package kurenai.imsyncbot.callback
//
//import kurenai.imsyncbot.telegram.send
//import moe.kurenai.tdlight.model.message.Message
//import moe.kurenai.tdlight.model.message.Update
//import moe.kurenai.tdlight.request.message.AnswerCallbackQuery
//import moe.kurenai.tdlight.util.getLogger
//
//abstract class Callback {
//
//    private val log = getLogger()
//
//    companion object {
//        const val CONTINUE = 1
//        const val END = 2
//
//        const val WITHOUT_ANSWER = 1
//
//        const val CONTINUE_WITHOUT_ANSWER = 11
//        const val END_WITHOUT_ANSWER = 12
//    }
//
//    open val name: String = this.javaClass.simpleName
//    open val method: String = ""
//
//    open suspend fun handle(update: Update, message: Message): Int {
//        return kotlin.runCatching {
//            handle0(update, message)
//        }.recover {
//            AnswerCallbackQuery(update.callbackQuery!!.id).apply {
//                text = "执行回调异常"
//                showAlert = true
//            }.send()
//            null
//        }.getOrThrow()?.let { it % 10 } ?: END
//    }
//
//    abstract suspend fun handle0(update: Update, message: Message): Int
//
//    open fun match(update: Update): Boolean {
//        return match(update.callbackQuery?.data ?: "").also {
//            if (it) {
//                log.debug("Match ${this.name} callback")
//            }
//        }
//    }
//
//    open fun match(text: String): Boolean {
//        return text.substringBefore(' ') == method
//    }
//
//    fun Update.getBody(): String {
//        return callbackQuery?.data?.substringAfter(' ') ?: ""
//    }
//
//    fun Update.getParams(): List<String> {
//        return callbackQuery?.data?.substringAfter(' ')?.trim()?.split(' ') ?: emptyList()
//    }
//
//}