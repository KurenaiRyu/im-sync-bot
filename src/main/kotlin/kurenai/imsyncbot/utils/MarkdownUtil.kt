package kurenai.imsyncbot.utils

object MarkdownUtil {

    private val formatChar = "_*[]()~`>#+-=|{}.!".toCharArray()

    fun String.format2Markdown(): String = MarkdownUtil.format(this)

    private fun format(target: String): String {
        var result = target
        for (c in formatChar) {
            result = target.replace(c.toString(), "\\$c")
        }
        return result
    }
}