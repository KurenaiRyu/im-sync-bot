package moe.fuqiuluo.unidbg.session

import com.tencent.mobileqq.channel.SsoPacket
import com.tencent.mobileqq.fe.FEKit
import kotlinx.coroutines.sync.Mutex
import kurenai.imsyncbot.bot.qq.login.qsign.EnvData
import kurenai.imsyncbot.bot.qq.login.qsign.QSign.BASE_PATH
import kurenai.imsyncbot.bot.qq.login.qsign.QSign.CONFIG
import kurenai.imsyncbot.bot.qq.login.qsign.vm.QSecVM

class Session(envData: EnvData) {
    internal val vm: QSecVM =
        QSecVM(BASE_PATH, envData, CONFIG.unidbg.dynarmic, CONFIG.unidbg.unicorn)
    internal val mutex = Mutex()

    init {
        vm.global["PACKET"] = arrayListOf<SsoPacket>()
        vm.global["mutex"] = Mutex(true)
        vm.global["qimei36"] = envData.qimei36.lowercase()
        vm.global["guid"] = envData.guid.lowercase()
        vm.init()
        FEKit.init(vm, envData.uin.toString())
    }
}