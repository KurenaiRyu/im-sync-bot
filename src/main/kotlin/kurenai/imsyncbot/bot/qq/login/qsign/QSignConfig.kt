@file:OptIn(ExperimentalSerializationApi::class)

package kurenai.imsyncbot.bot.qq.login.qsign

import com.tencent.mobileqq.dt.model.FEBound
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kurenai.imsyncbot.bot.qq.login.qsign.QSign.CONFIG
import kurenai.imsyncbot.utils.json
import java.io.File


object QSign {
    internal val CONFIG: QSignConfig
    internal val BASE_PATH: File = File("./config/qsign/").also {
        if (it.exists().not()) {
            it.mkdirs()
        }
    }

    init {
        if (!BASE_PATH.exists() ||
            !BASE_PATH.isDirectory ||
            !BASE_PATH.resolve("libfekit.so").exists() ||
            !BASE_PATH.resolve("libQSec.so").exists() ||
            !BASE_PATH.resolve("config.json").exists()
            || !BASE_PATH.resolve("dtconfig.json").exists()
        ) {
            error("The base path is invalid, perhaps it is not a directory or something is missing inside.")
        } else {
            FEBound.initAssertConfig(BASE_PATH)
            CONFIG = json.decodeFromString(QSignConfig.serializer(), BASE_PATH.resolve("config.json").readText())
        }
    }
}

@Serializable
data class EnvData(
    var uin: Long,
    @JsonNames("androidId", "android_id", "imei")
    var androidId: String,
    var guid: String,
    var qimei36: String,

    var qua: String = CONFIG.protocol.qua,
    var version: String = CONFIG.protocol.version,
    var code: String = CONFIG.protocol.code
)

@Serializable
data class Protocol(
    var qua: String,
    var version: String,
    var code: String
)

@Serializable
data class UnidbgConfig(
    var dynarmic: Boolean,
    var unicorn: Boolean,
    var debug: Boolean,
)

@Serializable
data class QSignConfig(
    var protocol: Protocol,
    var unidbg: UnidbgConfig,
)