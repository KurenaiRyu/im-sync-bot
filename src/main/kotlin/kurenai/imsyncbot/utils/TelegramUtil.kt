package kurenai.imsyncbot.utils

import kurenai.imsyncbot.ContextHolder
import kurenai.imsyncbot.cache.DelayItem
import mu.KotlinLogging
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import java.util.concurrent.DelayQueue

private val log = KotlinLogging.logger {}

class TelegramUtil {

    companion object {
        private val deleteQueue = DelayQueue<DelayItem<Pair<Long, Int>>>()

        fun deleteMsg(chatId: Long, messageId: Int, delay: Long) {
            deleteQueue.add(DelayItem(Pair(chatId, messageId), delay))
        }

        private fun deleteMsg() {
            log.info("Telegram delete message service started.")
            while (true) {
                try {
                    val delayItem = deleteQueue.take().item
                    ContextHolder.telegramBotClient.execute(
                        DeleteMessage.builder().chatId(delayItem.first.toString()).messageId(delayItem.second).build()
                    )
                } catch (e: InterruptedException) {
                    log.error(e.message, e)
                    break
                } catch (e: Exception) {
                    log.error(e.message, e)
                }
            }
            log.info("cache service stopped.")
        }

        init {
            val daemonThread = Thread { deleteMsg() }
            daemonThread.isDaemon = true
            daemonThread.name = "TgDelMsgSv"
            daemonThread.start()
        }
    }

}