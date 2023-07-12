package moe.fuqiuluo.unidbg.vm

import com.github.unidbg.arm.backend.DynarmicFactory
import com.github.unidbg.arm.backend.Unicorn2Factory
import com.github.unidbg.linux.android.AndroidEmulatorBuilder
import com.github.unidbg.linux.android.dvm.DalvikModule
import com.github.unidbg.linux.android.dvm.DvmClass
import com.github.unidbg.virtualmodule.android.AndroidModule
import kurenai.imsyncbot.bot.qq.login.qsign.QSign.CONFIG
import java.io.Closeable
import java.io.File

open class AndroidVM(packageName: String, dynarmic: Boolean, unicorn: Boolean) : Closeable {
    internal val emulator = AndroidEmulatorBuilder
        .for64Bit()
        .setProcessName(packageName)
        .apply {
            if (dynarmic) addBackendFactory(DynarmicFactory(true))
            if (unicorn) addBackendFactory(Unicorn2Factory(true))
        }
        .build()!!
    protected val memory = emulator.memory!!
    internal val vm = emulator.createDalvikVM()!!

    init {
        vm.setVerbose(CONFIG.unidbg.debug)
        val syscall = emulator.syscallHandler
        syscall.isVerbose = CONFIG.unidbg.debug
        syscall.setEnableThreadDispatcher(true)
        AndroidModule(emulator, vm).register(memory)
    }

    fun loadLibrary(soFile: File): DalvikModule {
        val dm = vm.loadLibrary(soFile, false)
        dm.callJNI_OnLoad(emulator)
        return dm
    }

    fun findClass(name: String, vararg interfaces: DvmClass): DvmClass {
        return vm.resolveClass(name, *interfaces)
    }

    override fun close() {
        this.emulator.close()
    }
}