@file:Suppress("UNCHECKED_CAST")

package moe.fuqiuluo.unidbg.env

import com.github.unidbg.linux.android.dvm.*
import com.tencent.mobileqq.channel.SsoPacket
import com.tencent.mobileqq.dt.model.FEBound
import com.tencent.mobileqq.qsec.qsecurity.DeepSleepDetector
import com.tencent.mobileqq.sign.QQSecuritySign
import kotlinx.coroutines.sync.Mutex
import kurenai.imsyncbot.bot.qq.login.qsign.EnvData
import kurenai.imsyncbot.bot.qq.login.qsign.vm.QSecVM
import kurenai.imsyncbot.utils.toHex
import moe.fuqiuluo.unidbg.vm.GlobalData
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

private val logger = LoggerFactory.getLogger(QSecJni::class.java)

typealias BytesObject = com.github.unidbg.linux.android.dvm.array.ByteArray

class QSecJni(
    val envData: EnvData,
    val vm: QSecVM,
    val global: GlobalData
) : AbstractJni() {
    override fun getStaticIntField(vm: BaseVM, dvmClass: DvmClass, signature: String): Int {
        if (signature == "android/os/Build\$VERSION->SDK_INT:I") {
            return 23
        }
        return super.getStaticIntField(vm, dvmClass, signature)
    }

    override fun getIntField(vm: BaseVM, dvmObject: DvmObject<*>, signature: String): Int {
        if (signature == "android/content/pm/ApplicationInfo->targetSdkVersion:I") {
            return 26
        }
        return super.getIntField(vm, dvmObject, signature)
    }

    override fun callVoidMethodV(vm: BaseVM, dvmObject: DvmObject<*>, signature: String, vaList: VaList) {
        if (signature == "com/tencent/mobileqq/fe/IFEKitLog->i(Ljava/lang/String;ILjava/lang/String;)V") {
            val tag = vaList.getObjectArg<StringObject>(0)
            val msg = vaList.getObjectArg<StringObject>(2)
            println(tag.value + "info: " + msg.value)
            return
        }
        if (signature == "com/tencent/mobileqq/fe/IFEKitLog->e(Ljava/lang/String;ILjava/lang/String;)V") {
            val tag = vaList.getObjectArg<StringObject>(0)
            val msg = vaList.getObjectArg<StringObject>(2)
            println(tag.value + "error: " + msg.value)
            return
        }
        if (signature == "com/tencent/mobileqq/channel/ChannelProxy->sendMessage(Ljava/lang/String;[BJ)V") {
            val cmd = vaList.getObjectArg<StringObject>(0).value
            val data = vaList.getObjectArg<BytesObject>(1).value
            val callbackId = vaList.getLongArg(2)
            val hex = data.toHex()

            if (callbackId == -1L) return

            println("uin = ${global["uin"]}, id = $callbackId, sendPacket(cmd = $cmd, data = $hex)")
            (global["PACKET"] as ArrayList<SsoPacket>).add(SsoPacket(cmd, hex, callbackId))
            (global["mutex"] as Mutex).also { if (it.isLocked) it.unlock() }
            return
        }

        if (signature == "com/tencent/mobileqq/qsec/qsecurity/QSec->updateO3DID(Ljava/lang/String;)V") {
            val o3did = vaList.getObjectArg<StringObject>(0).value
            global["o3did"] = o3did
            return
        }

        super.callVoidMethodV(vm, dvmObject, signature, vaList)
    }

    override fun getStaticObjectField(vm: BaseVM, dvmClass: DvmClass, signature: String): DvmObject<*> {
        if (signature == "com/tencent/mobileqq/qsec/qsecurity/QSecConfig->business_uin:Ljava/lang/String;") {
            return StringObject(vm, global["uin"] as String)
        }
        if (signature == "com/tencent/mobileqq/qsec/qsecurity/QSecConfig->business_seed:Ljava/lang/String;") {
            return StringObject(vm, global["seed"] as? String ?: "")
        }
        if (signature == "com/tencent/mobileqq/qsec/qsecurity/QSecConfig->business_guid:Ljava/lang/String;") {
            return StringObject(vm, global["guid"] as? String ?: "")
        }
        if (signature == "com/tencent/mobileqq/qsec/qsecurity/QSecConfig->business_o3did:Ljava/lang/String;") {
            return StringObject(vm, global["o3did"] as? String ?: "")
        }
        if (signature == "com/tencent/mobileqq/qsec/qsecurity/QSecConfig->business_q36:Ljava/lang/String;") {
            return StringObject(vm, global["qimei36"] as? String ?: "")
        }
        if (signature == "com/tencent/mobileqq/qsec/qsecurity/QSecConfig->business_qua:Ljava/lang/String;") {
            return StringObject(vm, this.vm.envData.qua)
        }
        return super.getStaticObjectField(vm, dvmClass, signature)
    }

    override fun getObjectField(vm: BaseVM, dvmObject: DvmObject<*>, signature: String): DvmObject<*> {
        if (signature == "android/content/pm/ApplicationInfo->nativeLibraryDir:Ljava/lang/String;") {
            return StringObject(
                vm,
                "/data/app/~~vbcRLwPxS0GyVfqT-nCYrQ==/com.tencent.mobileqq-xJKJPVp9lorkCgR_w5zhyA==/lib/arm64"
            )
        }
        return super.getObjectField(vm, dvmObject, signature)
    }

    override fun setObjectField(vm: BaseVM, dvmObject: DvmObject<*>, signature: String, value: DvmObject<*>) {
        if (signature == "com/tencent/mobileqq/sign/QQSecuritySign\$SignResult->token:[B") {
            val data = value.value as ByteArray
            (dvmObject as QQSecuritySign.SignResultObject).setToken(data)
            return
        }
        if (signature == "com/tencent/mobileqq/sign/QQSecuritySign\$SignResult->extra:[B") {
            val data = value.value as ByteArray
            (dvmObject as QQSecuritySign.SignResultObject).setExtra(data)
            return
        }
        if (signature == "com/tencent/mobileqq/sign/QQSecuritySign\$SignResult->sign:[B") {
            val data = value.value as ByteArray
            (dvmObject as QQSecuritySign.SignResultObject).setSign(data)
            return
        }
        super.setObjectField(vm, dvmObject, signature, value)
    }

    override fun callIntMethodV(vm: BaseVM, dvmObject: DvmObject<*>, signature: String, vaList: VaList): Int {
        if ("java/lang/String->hashCode()I" == signature) {
            return (dvmObject.value as String).hashCode()
        }
        return super.callIntMethodV(vm, dvmObject, signature, vaList)
    }

    override fun callStaticObjectMethodV(
        vm: BaseVM,
        dvmClass: DvmClass,
        signature: String,
        vaList: VaList
    ): DvmObject<*> {
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->mmKVValue(Ljava/lang/String;)Ljava/lang/String;") {
            return StringObject(
                vm, when (val key = vaList.getObjectArg<StringObject>(0).value) {
                    "TuringRiskID-TuringCache-20230511" -> ""
                    "o3_switch_Xwid", "o3_xwid_switch" -> global["o3_switch_Xwid"] as? String ?: "1"
                    "DeviceToken-oaid-V001" -> ""
                    "DeviceToken-MODEL-XX-V001" -> ""
                    "DeviceToken-ANDROID-ID-V001" -> ""
                    "DeviceToken-qimei36-V001" -> global["qimei36"] as? String ?: ""
                    "MQQ_SP_DEVICETOKEN_DID_DEVICEIDUUID_202207072241" -> UUID.randomUUID()
                        .toString() + "|" + this.vm.envData.version

                    "DeviceToken-APN-V001", "DeviceToken-TuringCache-V001", "DeviceToken-MAC-ADR-V001", "DeviceToken-wifissid-V001" -> "-1"
                    else -> error("Not support mmKVValue:$key")
                }
            )
        }
        if (signature == "android/provider/Settings\$System->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;") {
            val key = vaList.getObjectArg<StringObject>(1).value
            if (key == "android_id") {
                return StringObject(vm, envData.androidId.lowercase())
            }
        }
        if (signature == "com/tencent/mobileqq/fe/utils/DeepSleepDetector->getCheckResult()Ljava/lang/String;") {
            val result = (global["DeepSleepDetector"] as DeepSleepDetector).getCheckResult()
            return StringObject(vm, result.toString())
        }
        if (signature == "com/tencent/mobileqq/dt/model/FEBound->transform(I[B)[B") {
            val mode = vaList.getIntArg(0)
            val data = vaList.getObjectArg<DvmObject<*>>(1).value as ByteArray
            val result = FEBound.transform(mode, data)
            if (mode == 1)
                println("FEBound.transform(${data.toHex()}) => ${result?.toHex()}")
            return BytesObject(vm, result)
        }
        if (signature == "java/lang/ClassLoader->getSystemClassLoader()Ljava/lang/ClassLoader;") {
            return vm.resolveClass("java/lang/ClassLoader")
                .newObject(ClassLoader.getSystemClassLoader())
        }
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->getPropSafe(Ljava/lang/String;)Ljava/lang/String;") {
            return StringObject(
                vm, when (val key = vaList.getObjectArg<StringObject>(0).value) {
                    "ro.build.id" -> "TKQ1.220905.001"
                    "ro.build.display.id" -> "TKQ1.220905.001 test-keys"
                    "ro.product.device", "ro.product.name" -> "mondrian"
                    "ro.product.board" -> "taro"
                    "ro.product.manufacturer" -> "Xiaomi"
                    "ro.product.brand" -> "Redmi"
                    "ro.bootloader" -> "unknown"
                    "persist.sys.timezone" -> "Asia/Shanghai"
                    "ro.hardware" -> "qcom"
                    "ro.product.cpu.abilist" -> "arm64-v8a, armeabi-v7a, armeabi"
                    "ro.build.version.incremental" -> "V14.0.18.0.CNMLGB"
                    "ro.build.version.release" -> "12"
                    "ro.build.version.base_os", "ro.boot.container", "ro.vendor.build.fingerprint", "ro.build.expect.bootloader", "ro.build.expect.baseband" -> ""
                    "ro.build.version.security_patch" -> "2077-2-29"
                    "ro.build.version.preview_sdk" -> "0"
                    "ro.build.version.codename", "ro.build.version.all_codenames" -> "REL"
                    "ro.build.type" -> "user"
                    "ro.build.tags" -> "release-keys"
                    "ro.treble.enabled" -> "true"
                    "ro.build.date.utc" -> "1673390476"
                    "ro.build.user" -> ""
                    "ro.build.host" -> "build"
                    "net.bt.name" -> "Android"
                    "ro.build.characteristics" -> "default"
                    "ro.build.description" -> "mondrian-user 12 TKQ1.220905.001 release-keys"
                    "ro.product.locale" -> "zh-CN"
                    "ro.build.flavor" -> "full_miui_64-user"
                    "ro.config.ringtone" -> "Ring_Synth_04.ogg"
                    else -> error("Not support prop:$key")
                }
            )
        }
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->getAppVersionName(Ljava/lang/String;)Ljava/lang/String;") {
            return StringObject(
                vm, when (val key = vaList.getObjectArg<StringObject>(0).value) {
                    "empty" -> this.vm.envData.version
                    else -> error("Not support getAppVersionName:$key")
                }
            )
        }
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->getAppVersionCode(Ljava/lang/String;)Ljava/lang/String;") {
            return StringObject(
                vm, when (val key = vaList.getObjectArg<StringObject>(0).value) {
                    "empty" -> this.vm.envData.code
                    else -> error("Not support getAppVersionCode:$key")
                }
            )
        }
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->getAppInstallTime(Ljava/lang/String;)Ljava/lang/String;") {
            return StringObject(
                vm, when (val key = vaList.getObjectArg<StringObject>(0).value) {
                    "empty" -> (System.currentTimeMillis() - 10000).toString()
                    else -> error("Not support getAppVersionCode:$key")
                }
            )
        }
        if (
            signature == "com/tencent/mobileqq/dt/app/Dtc->getDensity(Ljava/lang/String;)Ljava/lang/String;" ||
            signature == "com/tencent/mobileqq/dt/app/Dtc->getFontDpi(Ljava/lang/String;)Ljava/lang/String;"
        ) {
            return StringObject(
                vm, when (val key = vaList.getObjectArg<StringObject>(0).value) {
                    "empty" -> "1.3125"
                    else -> error("Not support getAppVersionCode:$key")
                }
            )
        }
        if ("com/tencent/mobileqq/dt/app/Dtc->getScreenSize(Ljava/lang/String;)Ljava/lang/String;" == signature) {
            return StringObject(vm, "[800,1217]")
        }
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->getStorage(Ljava/lang/String;)Ljava/lang/String;") {
            return StringObject(vm, "137438953471")
        }
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->systemGetSafe(Ljava/lang/String;)Ljava/lang/String;") {
            return StringObject(
                vm, when (val key = vaList.getObjectArg<StringObject>(0).value) {
                    "user.locale" -> "zh-CN"
                    "http.agent" -> "Dalvik/2.1.0 (Linux; U; Android 12.0.0; 114514 Build/O11019)"
                    "java.vm.version" -> "2.1.0"
                    "os.version" -> "3.18.79"
                    "persist.sys.timezone" -> "-1"
                    "java.runtime.version" -> "0.9"
                    "java.boot.class.path" -> "/system/framework/core-oj.jar:/system/framework/core-libart.jar:/system/framework/conscrypt.jar:/system/frameworkhttp.jar:/system/framework/bouncycastle.jar:/system/framework/apache-xml.jar:/system/framework/legacy-test.jar:/system/framework/ext.jar:/system/framework/framework.jar:/system/framework/telephony-common.jar:/system/frameworkoip-common.jar:/system/framework/ims-common.jar:/system/framework/org.apache.http.legacy.boot.jar:/system/framework/android.hidl.base-V1.0-java.jar:/system/framework/android.hidl.manager-V1.0-java.jar:/system/framework/mediatek-common.jar:/system/framework/mediatek-framework.jar:/system/framework/mediatek-telephony-common.jar:/system/framework/mediatek-telephony-base.jar:/system/framework/mediatek-ims-common.jar:/system/framework/mediatek-telecom-common.jar:/system/framework/mediatek-cta.jar"
                    else -> error("Not support systemGetSafe:$key")
                }
            )
        }
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->getIME(Ljava/lang/String;)Ljava/lang/String;") {
            return StringObject(vm, "com.netease.nemu_vinput.nemu/com.android.inputmethodcommon.SoftKeyboard")
        }
        return super.callStaticObjectMethodV(vm, dvmClass, signature, vaList)
    }

    override fun callStaticVoidMethodV(vm: BaseVM, dvmClass: DvmClass, signature: String, vaList: VaList) {
        if (signature == "com/tencent/mobileqq/fe/utils/DeepSleepDetector->stopCheck()V") {
            if ("DeepSleepDetector" in global) {
                (global["DeepSleepDetector"] as DeepSleepDetector).stopped = true
            }
            return
        }
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->mmKVSaveValue(Ljava/lang/String;Ljava/lang/String;)V") {
            val key = vaList.getObjectArg<StringObject>(0).value
            val value = vaList.getObjectArg<StringObject>(1).value
            global[key] = value
            return
        }
        if (signature == "com/tencent/mobileqq/dt/app/Dtc->saveList(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V") {
            return
        }
        super.callStaticVoidMethodV(vm, dvmClass, signature, vaList)
    }

    override fun callObjectMethodV(
        vm: BaseVM,
        dvmObject: DvmObject<*>,
        signature: String,
        vaList: VaList
    ): DvmObject<*> {
        if (signature == "android/content/Context->getApplicationInfo()Landroid/content/pm/ApplicationInfo;") {
            return vm.resolveClass("android/content/pm/ApplicationInfo").newObject(null)
        }
        if (signature == "android/content/Context->getFilesDir()Ljava/io/File;") {
            return vm
                .resolveClass("java.io.File")
                .newObject(File("/data/user/0/com.tencent.mobileqq/files"))
        }
        if (signature == "android/content/Context->getContentResolver()Landroid/content/ContentResolver;") {
            return vm.resolveClass("android/content/ContentResolver")
                .newObject(null)
        }
        if (signature == "android/content/Context->getPackageResourcePath()Ljava/lang/String;") {
            return StringObject(
                vm,
                "/data/app/~~vbcRLwPxS0GyVfqT-nCYrQ==/com.tencent.mobileqq-xJKJPVp9lorkCgR_w5zhyA==/base.apk"
            )
        }
        if (signature == "android/content/Context->getPackageName()Ljava/lang/String;") {
            return StringObject(vm, "com.tencent.mobileqq")
        }
        if (signature == "java/lang/ClassLoader->loadClass(Ljava/lang/String;)Ljava/lang/Class;") {
            val name = vaList.getObjectArg<StringObject>(0).value
            val loader = dvmObject.value as ClassLoader
            try {
                return vm
                    .resolveClass("java/lang/Class")
                    .newObject(loader.loadClass(name))
            } catch (e: ClassNotFoundException) {
                vm.throwException(
                    vm
                        .resolveClass("java.lang.ClassNotFoundException")
                        .newObject(e)
                )
            }
            return vm
                .resolveClass("java/lang/Class")
                .newObject(null)
        }
        return super.callObjectMethodV(vm, dvmObject, signature, vaList)
    }

    override fun newObjectV(vm: BaseVM, dvmClass: DvmClass, signature: String, vaList: VaList): DvmObject<*> {
        if (signature == "java/io/File-><init>(Ljava/lang/String;)V") {
            val path = vaList.getObjectArg<StringObject>(0).value
            return vm
                .resolveClass("java/io/File")
                .newObject(File(path))
        }
        if (signature == "com/tencent/mobileqq/sign/QQSecuritySign\$SignResult-><init>()V") {
            return QQSecuritySign.SignResultObject(vm)
        }
        return super.newObjectV(vm, dvmClass, signature, vaList)
    }

    override fun callBooleanMethodV(
        vm: BaseVM,
        dvmObject: DvmObject<*>,
        signature: String,
        vaList: VaList
    ): Boolean {
        if (signature == "java/io/File->canRead()Z") {
            val file = dvmObject.value as File
            if (
                file.toString() == "\\data\\data\\com.tencent.mobileqq\\.." ||
                file.toString() == "/data/data/com.tencent.mobileqq/.." ||
                file.toString() == "/data/data/" ||
                file.toString() == "/data/data"
            ) {
                return false
            }
        }
        return super.callBooleanMethodV(vm, dvmObject, signature, vaList)
    }

    override fun callObjectMethod(
        vm: BaseVM,
        dvmObject: DvmObject<*>,
        signature: String,
        varArg: VarArg
    ): DvmObject<*> {
        return super.callObjectMethod(vm, dvmObject, signature, varArg)
    }
}