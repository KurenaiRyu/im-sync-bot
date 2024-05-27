import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsoupTest {


    @Test
    fun test() {
        val content = "Test Content with <img src=\"http://localhost:5500/asset/testid\"/>"
        val doc = Jsoup.parse(content, Parser.xmlParser())
        assertEquals("Test Content with ", doc.childNodes()[0].toString())
        assertEquals("http://localhost:5500/asset/testid", doc.getElementsByTag("img").attr("src"))
    }

    @Test
    fun testMultiTags() {
        val content =
            "<quote chronocat:seq=\"524381\"><author id=\"2655297208\" name=\"夏影\" avatar=\"http://thirdqq.qlogo.cn/headimg_dl?dst_uin=2655297208&amp;spec=640\"/>没看gbc台长后面激情输出9分钟</quote><at id=\"2655297208\" name=\"夏影\"/> 快6k评论了<chronocat:face id=\"317\" name=\"[菜汪]\" platform=\"chronocat\"/>"
        val doc = Jsoup.parse(content, Parser.xmlParser())
        assertTrue(doc.allElements[0].getElementsByTag("author").size > 0)
    }
}