package kurenai.imsyncbot

enum class BotConfigKey(
    val value: String,
    val description: String = ""
) {

    MASTER_ID("masterId"),
    MASTER_USERNAME("masterUsername"),
    MASTER_CHAT_ID("masterChatId"),
}