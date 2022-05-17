package kurenai.imsyncbot.command


annotation class Command(
    val command: String,
    val aliases: Array<String> = [],
    val description: String = ""
)
