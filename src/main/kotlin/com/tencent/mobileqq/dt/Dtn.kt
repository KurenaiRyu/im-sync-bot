package com.tencent.mobileqq.dt

import com.github.unidbg.linux.android.dvm.DvmObject
import kurenai.imsyncbot.bot.qq.login.qsign.vm.QSecVM

object Dtn {
    fun initContext(vm: QSecVM, context: DvmObject<*>) {
        runCatching {
            vm.newInstance("com/tencent/mobileqq/dt/Dtn", unique = true)
                .callJniMethod(vm.emulator, "initContext(Landroid/content/Context;)V", context)
        }.onFailure {
            vm.newInstance("com/tencent/mobileqq/dt/Dtn", unique = true)
                .callJniMethod(
                    vm.emulator, "initContext(Landroid/content/Context;Ljava/lang/String;)V",
                    context, "/data/user/0/com.tencent.mobileqq/files/5463306EE50FE3AA"
                )
        }
    }

    fun initLog(vm: QSecVM, logger: DvmObject<*>) {
        vm.newInstance("com/tencent/mobileqq/dt/Dtn", unique = true)
            .callJniMethod(vm.emulator, "initLog(Lcom/tencent/mobileqq/fe/IFEKitLog;)V", logger)
    }

    fun initUin(vm: QSecVM, uin: String) {
        vm.newInstance("com/tencent/mobileqq/dt/Dtn", unique = true)
            .callJniMethod(vm.emulator, "initUin(Ljava/lang/String;)V", uin)
    }
}