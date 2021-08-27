package kurenai.mybot.handler

import kurenai.mybot.telegram.TelegramBotClient
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

interface Handler : Comparable<Handler> {
    suspend fun preHandle(client: TelegramBotClient?, update: Update?): Boolean {
        return true
    }

    /**
     * @param update
     * @return true 为继续执行，false中断
     */
    @Throws(Exception::class)
    suspend fun handleTgMessage(message: Message): Boolean {
        return true
    }

    @Throws(Exception::class)
    suspend fun handleTgEditMessage(message: Message): Boolean {
        return true
    }

    suspend fun postHandle(message: Message): Boolean {
        return true
    }

    @Throws(Exception::class)
    suspend fun handleQQGroupMessage(event: GroupAwareMessageEvent): Boolean {
        return true
    }

    @Throws(TelegramApiException::class)
    suspend fun handleQQRecall(event: MessageRecallEvent): Boolean {
        return true
    }

    fun order(): Int {
        return 100
    }

    override fun compareTo(other: Handler): Int {
        return this.order() - other.order()
    }

    fun handleName(): String {
        return this.javaClass.simpleName
    }

}