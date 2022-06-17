package kurenai.imsyncbot.utils

import moe.kurenai.tdlight.model.MessageEntityType
import moe.kurenai.tdlight.model.message.MessageEntity

/**
 * @author Kurenai
 * @since 6/15/2022 23:27:24
 */

class MessageBuilder {

    companion object {
        fun of(text: String, entity: String): MessageBuilder {
            val builder = MessageBuilder()
            builder.list.add(Entity(text, entity))
            return builder
        }
    }

    val list = ArrayList<Entity>()

    fun MessageBuilder.plus(text: String, type: String) {
        list.add(Entity(text, type))
    }

    fun build() {
        val sb = StringBuilder()
        val entites = ArrayList<MessageEntity>()
        var offset = 0
        for ((text, type) in list) {
            sb.append(text)
            val length = text.length
            entites.add(when (type) {
                MessageEntityType.TEXT_MENTION -> {
                    MessageEntity(type, offset, length).apply {
                    }
                }
                else -> {
                    MessageEntity(type, offset, length)
                }
            })
            offset += length
        }
    }


    data class Entity(
        val text: String,
        val type: String
    )
}