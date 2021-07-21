package kurenai.mybot.handler

import kurenai.mybot.QQBotClient
import kurenai.mybot.TelegramBotClient
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

interface Handler : Comparable<Handler> {
    fun preHandle(client: TelegramBotClient?, update: Update?): Boolean {
        return true
    }

    /**
     * @param client
     * @param qqClient
     * @param update
     * @return true 为继续执行，false中断
     */
    @Throws(Exception::class)
    fun handleMessage(client: TelegramBotClient, qqClient: QQBotClient, update: Update?, message: Message): Boolean {
        return true
    }

    @Throws(Exception::class)
    fun handleEditMessage(client: TelegramBotClient, qqClient: QQBotClient, update: Update?, message: Message): Boolean {
        return true
    }

    fun postHandle(client: TelegramBotClient?, update: Update?, message: Message?): Boolean {
        return true
    }

    @Throws(Exception::class)
    fun handle(client: QQBotClient?, telegramBotClient: TelegramBotClient, event: GroupAwareMessageEvent): Boolean {
        return true
    }

    @Throws(TelegramApiException::class)
    fun handleRecall(client: QQBotClient?, telegramBotClient: TelegramBotClient, event: MessageRecallEvent): Boolean {
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