package kurenai.mybot.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test

class RetryUtilTest {

    @Test
    fun test() {
        CoroutineScope(Dispatchers.Default).launch {
            RetryUtil.retry(1) {
                Thread.sleep(500)
                throw Exception()
            }
        }
        Thread.sleep(15000L)
    }
}

