package kurenai.imsyncbot.callback

import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update

interface Callback {

    suspend fun handle(update: Update, message: Message): Boolean {
        return false
    }

}