import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class JsoupTest {

    private val content = "Test Content with <img src=\"http://localhost:5500/asset/testid\"/>"

    @Test
    fun test() {
        println(Jsoup.parse(content).body().text())
        println(Jsoup.parse(content).body().getElementsByTag("img").attr("src"))
    }
}