package com.tencent.mobileqq.sign

import com.github.unidbg.linux.android.dvm.BaseVM
import com.github.unidbg.linux.android.dvm.DvmObject
import kurenai.imsyncbot.bot.qq.login.qsign.vm.QSecVM
import net.mamoe.mirai.utils.EMPTY_BYTE_ARRAY
import net.mamoe.mirai.utils.toByteArray

object QQSecuritySign {
    @Suppress("ArrayInDataClass")
    data class SignResult(
        var sign: ByteArray = EMPTY_BYTE_ARRAY,
        var extra: ByteArray = EMPTY_BYTE_ARRAY,
        var token: ByteArray = EMPTY_BYTE_ARRAY
    )

    class SignResultObject(vm: BaseVM, signResult: SignResult = SignResult()) :
        DvmObject<SignResult>(vm.resolveClass("com/tencent/mobileqq/sign/QQSecuritySign\$SignResult"), signResult) {
        fun setToken(token: ByteArray) {
            value.token = token
        }

        fun setSign(sign: ByteArray) {
            value.sign = sign
        }

        fun setExtra(extra: ByteArray) {
            value.extra = extra
        }
    }

    fun initSafeMode(vm: QSecVM, safe: Boolean) {
        vm.newInstance("com/tencent/mobileqq/sign/QQSecuritySign", unique = true)
            .callJniMethod(vm.emulator, "initSafeMode(Z)V", safe)
    }

    fun dispatchEvent(vm: QSecVM, cmd: String = "", uin: String = "0") {
        vm.newInstance("com/tencent/mobileqq/sign/QQSecuritySign", unique = true)
            .callJniMethod(
                vm.emulator,
                "dispatchEvent(Ljava/lang/String;Ljava/lang/String;Lcom/tencent/mobileqq/fe/EventCallback;)V",
                cmd,
                uin,
                vm.newInstance("com/tencent/mobileqq/fe/EventCallback", unique = true)
            )
    }

    fun requestToken(vm: QSecVM) {
        vm.newInstance("com/tencent/mobileqq/sign/QQSecuritySign", unique = true)
            .callJniMethod(vm.emulator, "requestToken()V")
    }

    fun getSign(
        vm: QSecVM,
        qua: String,
        cmd: String,
        buffer: ByteArray,
        seq: Int,
        uin: String,
        qsec: DvmObject<*> = vm.newInstance("com/tencent/mobileqq/qsec/qsecurity/QSec", unique = true)
    ): SignResultObject {
        return (vm.newInstance("com/tencent/mobileqq/sign/QQSecuritySign", unique = true)
            .callJniMethodObject(
                vm.emulator,
                "getSign(Lcom/tencent/mobileqq/qsec/qsecurity/QSec;Ljava/lang/String;Ljava/lang/String;[B[BLjava/lang/String;)Lcom/tencent/mobileqq/sign/QQSecuritySign\$SignResult;",
                qsec,
                qua,
                cmd,
                buffer,
                seq.toByteArray(),
                uin
            ) as SignResultObject)
    }
}