package kurenai.imsyncbot.utils

object MarkdownUtil {
    fun String.format2Markdown(): String = MarkdownUtil.format(this)

    private fun format(target: String): String {
        return target.replace("|", "\\|")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace(".", "\\.")
            .replace("-", "\\-")
            .replace("_", "\\_")
            .replace("#", "\\#")
            .replace("*", "\\*")
            .replace("`", "\\`")
            .replace("~", "\\~")
            .replace("+", "\\+")
            .replace("!", "\\!")
            .replace("=", "\\=")
    }
}