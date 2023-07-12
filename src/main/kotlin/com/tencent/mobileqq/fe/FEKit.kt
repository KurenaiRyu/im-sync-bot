package com.tencent.mobileqq.fe

import com.tencent.mobileqq.channel.ChannelManager
import com.tencent.mobileqq.dt.Dtn
import com.tencent.mobileqq.qsec.qsecurity.DeepSleepDetector
import com.tencent.mobileqq.qsec.qsecurity.QSec
import com.tencent.mobileqq.sign.QQSecuritySign
import kurenai.imsyncbot.bot.qq.login.qsign.vm.QSecVM

object FEKit {
    fun init(vm: QSecVM, uin: String = "0") {
        if ("fekit" in vm.global) return
        vm.global["uin"] = uin

        QQSecuritySign.initSafeMode(vm, false)
        QQSecuritySign.dispatchEvent(vm, "Kicked", uin)

        val context = vm.newInstance("android/content/Context", unique = true)
        Dtn.initContext(vm, context)
        Dtn.initLog(vm, vm.newInstance("com/tencent/mobileqq/fe/IFEKitLog"))
        Dtn.initUin(vm, uin)

        if ("DeepSleepDetector" !in vm.global) {
            vm.global["DeepSleepDetector"] = DeepSleepDetector()
        }

        ChannelManager.setChannelProxy(vm, vm.newInstance("com/tencent/mobileqq/channel/ChannelProxy"))
        ChannelManager.initReport(vm, vm.envData.qua, "6.100.248") // TODO(maybe check?)

        QSec.doSomething(vm, context)
    }

    fun changeUin(vm: QSecVM, uin: String) {
        vm.global["uin"] = uin

        Dtn.initUin(vm, uin)
        QQSecuritySign.dispatchEvent(vm, "Kicked", uin)
    }
}