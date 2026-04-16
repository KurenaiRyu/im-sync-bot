package kurenai.imsyncbot.bot.discord

import kurenai.imsyncbot.utils.getLogger
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class MessageReceiveListener : ListenerAdapter() {

    companion object {
        private val log = getLogger()
    }

    override fun onGenericEvent(event: GenericEvent) {
        log.trace("Received a event: {}", event)

    }


}