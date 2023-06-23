package kurenai.imsyncbot.handler.qq

import kotlinx.serialization.json.Json
import kurenai.imsyncbot.bot.qq.JsonMessageContent
import org.junit.jupiter.api.Test

/**
 * @author Kurenai
 * @since 2023/3/19 0:15
 */
class GroupMessageContextTest {

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    @Test
    fun testJsonMessageContentDeserialize() {
        listOf(
            "{\"app\":\"com.tencent.structmsg\",\"config\":{\"ctime\":1679155720,\"forward\":true,\"token\":\"992193f366607f66ab384fde973c3f5a\",\"type\":\"normal\"},\"desc\":\"新闻\",\"extra\":{\"app_type\":1,\"appid\":100951776,\"msg_seq\":7211918903902515173,\"uin\":3101368113},\"meta\":{\"news\":{\"action\":\"\",\"android_pkg_name\":\"\",\"app_type\":1,\"appid\":100951776,\"ctime\":1679155720,\"desc\":\"UP主：-こころ--房间号：23765713\",\"jumpUrl\":\"https://b23.tv/bupa3wh?share_medium=android&share_source=qq&bbid=XY0D9720740624BFDD51ECAD0939421C29103&ts=1679155713907\",\"preview\":\"https://pic.ugcimg.cn/f44db0e547d6f38d4031cbd88d4168af/jpg1\",\"source_icon\":\"https://open.gtimg.cn/open/app_icon/00/95/17/76/100951776_100_m.png?t=1676449478\",\"source_url\":\"\",\"tag\":\"哔哩哔哩\",\"title\":\"md\",\"uin\":3101368113}},\"prompt\":\"[分享]md\",\"ver\":\"0.0.0.1\",\"view\":\"news\"}",
            "{\"app\":\"com.tencent.structmsg\",\"desc\":\"新闻\",\"bizsrc\":\"\",\"view\":\"news\",\"ver\":\"0.0.0.1\",\"prompt\":\"[分享]3月18日成都《利兹与青鸟》放映会\",\"meta\":{\"news\":{\"action\":\"\",\"android_pkg_name\":\"\",\"app_type\":1,\"appid\":101053870,\"ctime\":1678233592,\"desc\":\"一条来自NGA的热点内容，快来参与讨论吧\",\"jumpUrl\":\"https://ngabbs.com/read.php?tid=35594578&_fu=60055688%2C1\",\"preview\":\"https://pic.ugcimg.cn/30598dc32d7c22e15b74e5ea4c392e9f/jpg1\",\"source_icon\":\"http://i.gtimg.cn/open/app_icon/01/05/38/70//101053870_100_ios.png\",\"source_url\":\"\",\"tag\":\"NGA玩家社区\",\"title\":\"3月18日成都《利兹与青鸟》放映会\",\"uin\":1453427102}},\"config\":{\"ctime\":1678233592,\"forward\":true,\"token\":\"56389f24604d74d405d322b631f21ee0\",\"type\":\"normal\"}}",
            "{ \"app\": \"com.tencent.structmsg\", \"config\": { \"ctime\": 1682927842, \"forward\": true, \"token\": \"15d9a6a9553ec976daf1b54e216c4973\", \"type\": \"normal\" }, \"desc\": \"视频\", \"meta\": { \"video\": { \"app_type\": 1, \"appid\": 100507190, \"ctime\": 1682927842, \"desc\": \"排了一个半小时乐 还在排 二次元轰炸上海的证据（误 #jumpshop  #cp\", \"jumpUrl\": \"https://www.xiaohongshu.com/discovery/item/644f3af2000000001303d54b?app_platform=ios&app_version=7.72.2&share_from_user_hidden=true&type=video&xhsshare=QQ&appuid=5dea0d1000000000010057c1&apptime=1682927829\", \"preview\": \"https://pic.ugcimg.cn/613c2ac32f6d74cf3ff73b8fe3b0a9a3/jpg1\", \"source_icon\": \"https://open.gtimg.cn/open/app_icon/00/50/71/90/100507190_100_m.png?t=1682424448\", \"tag\": \"小红书\", \"title\": \"请欣赏——Jumpshop军训现场\", \"uin\": 756506614 } }, \"prompt\": \"[分享]请欣赏——Jumpshop军训现场\", \"ver\": \"0.0.0.1\", \"view\": \"video\" }"
        ).forEach {
            json.decodeFromString(JsonMessageContent.serializer(), it)
        }

    }
}