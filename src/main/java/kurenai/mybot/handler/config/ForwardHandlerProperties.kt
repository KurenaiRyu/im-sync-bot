package kurenai.mybot.handler.config

import kurenai.mybot.handler.HandlerProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.util.*

@ConstructorBinding
@ConfigurationProperties(prefix = "bot.handler.forward")
data class ForwardHandlerProperties(var master: Long = 0, var group: Group, var member: Member) : HandlerProperties() {

    data class Group(
        var defaultQQ: Long = 0, var defaultTelegram: Long = 0,
        var qqTelegram: MutableMap<Long, Long> = Collections.emptyMap(),
        var telegramQq: MutableMap<Long, Long> = Collections.emptyMap(),
    ) {
        init {
            qqTelegram.forEach { (k: Long, v: Long) -> telegramQq.putIfAbsent(v, k) }
            telegramQq.forEach { (k: Long, v: Long) -> qqTelegram.putIfAbsent(v, k) }
        }
    }

    data class Member(var bindingName: MutableMap<Long, String> = Collections.emptyMap())
}