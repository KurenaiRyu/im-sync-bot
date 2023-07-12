package com.tencent.crypt

import java.io.ByteArrayOutputStream
import java.util.*

class Crypt {
    // 指向当前的明文块
    lateinit var plain: ByteArray

    // 这指向前面一个明文块
    lateinit var prePlain: ByteArray

    // 输出的密文或者明文
    lateinit var out: ByteArray

    // 当前加密的密文位置和上一次加密的密文块位置，他们相差8
    private var crypt = 0
    private var preCrypt = 0

    // 当前处理的加密解密块的位置
    private var pos = 0

    // 填充数
    private var padding = 0

    // 密钥
    lateinit var key: ByteArray

    // 用于加密时，表示当前是否是第一个8字节块，因为加密算法是反馈的
    // 但是最开始的8个字节没有反馈可用，所有需要标明这种情况
    private var header = true

    // 这个表示当前解密开始的位置，之所以要这么一个变量是为了避免当解密到最后时
    // 后面已经没有数据，这时候就会出错，这个变量就是用来判断这种情况免得出错
    private var contextStart = 0

    // 字节输出流
    private val bas: ByteArrayOutputStream

    /**
     * 构造函数
     */
    init {
        bas = ByteArrayOutputStream(8)
    }

    /**
     * 解密
     *
     * @param in     密文
     * @param offset 密文开始的位置
     * @param len    密文长度
     * @param k      密钥
     * @return 明文
     */
    fun decrypt(`in`: ByteArray, offset: Int, len: Int, k: ByteArray?): ByteArray? {
        // 检查密钥
        if (k == null) return null
        preCrypt = 0
        crypt = preCrypt
        key = k
        var count: Int
        var m = ByteArray(offset + 8)

        // 因为QQ消息加密之后至少是16字节，并且肯定是8的倍数，这里检查这种情况
        if (len % 8 != 0 || len < 16) return null
        // 得到消息的头部，关键是得到真正明文开始的位置，这个信息存在第一个字节里面，所以其用解密得到的第一个字节与7做与
        prePlain = decipher(`in`, offset)
        pos = prePlain[0].toInt() and 0x7
        // 得到真正明文的长度
        count = len - pos - 10
        // 如果明文长度小于0，那肯定是出错了，比如传输错误之类的，返回
        if (count < 0) return null

        // 这个是临时的preCrypt，和加密时第一个8字节块没有prePlain一样，解密时
        // 第一个8字节块也没有preCrypt，所有这里建一个全0的
        for (i in offset until m.size) m[i] = 0
        // 通过了上面的代码，密文应该是没有问题了，我们分配输出缓冲区
        out = ByteArray(count)
        // 设置preCrypt的位置等于0，注意目前的preCrypt位置是指向m的，因为java没有指针，所以我们在后面要控制当前密文buf的引用
        preCrypt = 0
        // 当前的密文位置，为什么是8不是0呢？注意前面我们已经解密了头部信息了，现在当然该8了
        crypt = 8
        // 自然这个也是8
        contextStart = 8
        // 加1，和加密算法是对应的
        pos++

        // 开始跳过头部，如果在这个过程中满了8字节，则解密下一块
        // 因为是解密下一块，所以我们有一个语句 m = in，下一块当然有preCrypt了，我们不再用m了
        // 但是如果不满8，这说明了什么？说明了头8个字节的密文是包含了明文信息的，当然还是要用m把明文弄出来
        // 所以，很显然，满了8的话，说明了头8个字节的密文除了一个长度信息有用之外，其他都是无用的填充
        padding = 1
        while (padding <= 2) {
            if (pos < 8) {
                pos++
                padding++
            }
            if (pos == 8) {
                m = `in`
                decrypt8Bytes(`in`, offset, len)
            }
        }

        // 这里是解密的重要阶段，这个时候头部的填充都已经跳过了，开始解密
        // 注意如果上面一个while没有满8，这里第一个if里面用的就是原始的m，否则这个m就是in了
        var i = 0
        while (count != 0) {
            if (pos < 8) {
                out[i] = (m[offset + preCrypt + pos].toInt() xor prePlain[pos].toInt()).toByte()
                i++
                count--
                pos++
            }
            if (pos == 8) {
                m = `in`
                preCrypt = crypt - 8
                decrypt8Bytes(`in`, offset, len)
            }
        }

        // 最后的解密部分，上面一个while已经把明文都解出来了，就剩下尾部的填充了，应该全是0
        // 所以这里有检查是否解密了之后是不是0，如果不是的话那肯定出错了，返回null
        padding = 1
        while (padding < 8) {
            if (pos < 8) {
                if (m[offset + preCrypt + pos].toInt() xor prePlain[pos].toInt() != 0) return null
                pos++
            }
            if (pos == 8) {
                m = `in`
                preCrypt = crypt
                decrypt8Bytes(`in`, offset, len)
            }
            padding++
        }
        return out
    }

    fun decrypt(input: ByteArray, k: ByteArray?): ByteArray? {
        return decrypt(input, 0, input.size, k)
    }

    fun encrypt(input: ByteArray, offset: Int, len: Int, k: ByteArray?): ByteArray {
        // 检查密钥
        var len = len
        if (k == null) return input
        plain = ByteArray(8)
        prePlain = ByteArray(8)
        padding = 0
        preCrypt = 0
        crypt = preCrypt
        key = k
        header = true

        // 计算头部填充字节数
        pos = (len + 0x0A) % 8
        if (pos != 0) pos = 8 - pos
        // 计算输出的密文长度
        out = ByteArray(len + pos + 10)
        // 这里的操作把pos存到了plain的第一个字节里面
        // 0xF8后面三位是空的，正好留给pos，因为pos是0到7的值，表示文本开始的字节位置
        plain[0] = (rand() and 0xF8 or pos).toByte()

        // 这里用随机产生的数填充plain[1]到plain[pos]之间的内容
        for (i in 1..pos) plain[i] = (rand() and 0xFF).toByte()
        pos++
        // 这个就是prePlain，第一个8字节块当然没有prePlain，所以我们做一个全0的给第一个8字节块
        for (i in 0..7) prePlain[i] = 0x0

        // 继续填充2个字节的随机数，这个过程中如果满了8字节就加密之
        padding = 1
        while (padding <= 2) {
            if (pos < 8) {
                plain[pos++] = (rand() and 0xFF).toByte()
                padding++
            }
            if (pos == 8) encrypt8Bytes()
        }

        // 头部填充完了，这里开始填真正的明文了，也是满了8字节就加密，一直到明文读完
        var i = offset
        while (len > 0) {
            if (pos < 8) {
                plain[pos++] = input[i++]
                len--
            }
            if (pos == 8) encrypt8Bytes()
        }

        // 最后填上0，以保证是8字节的倍数
        padding = 1
        while (padding <= 7) {
            if (pos < 8) {
                plain[pos++] = 0x0
                padding++
            }
            if (pos == 8) encrypt8Bytes()
        }
        return out
    }

    fun encrypt(`in`: ByteArray, k: ByteArray?): ByteArray {
        return encrypt(`in`, 0, `in`.size, k)
    }

    private fun encipher(`in`: ByteArray): ByteArray {
        // 迭代次数，16次
        var loop = 0x10
        // 得到明文和密钥的各个部分，注意java没有无符号类型，所以为了表示一个无符号的整数
        // 我们用了long，这个long的前32位是全0的，我们通过这种方式模拟无符号整数，后面用到的long也都是一样的
        // 而且为了保证前32位为0，需要和0xFFFFFFFF做一下位与
        var y = getUnsignedInt(`in`, 0, 4)
        var z = getUnsignedInt(`in`, 4, 4)
        val a = getUnsignedInt(key, 0, 4)
        val b = getUnsignedInt(key, 4, 4)
        val c = getUnsignedInt(key, 8, 4)
        val d = getUnsignedInt(key, 12, 4)
        // 这是算法的一些控制变量，为什么delta是0x9E3779B9呢？
        // 这个数是TEA算法的delta，实际是就是(sqr(5) - 1) * 2^31 (根号5，减1，再乘2的31次方)
        var sum: Long = 0
        var delta: Long = -0x61c88647
        delta = delta and 0xFFFFFFFFL

        // 开始迭代了，乱七八糟的，我也看不懂，反正和DES之类的差不多，都是这样倒来倒去
        while (loop-- > 0) {
            sum += delta
            sum = sum and 0xFFFFFFFFL
            y += (z shl 4) + a xor z + sum xor (z ushr 5) + b
            y = y and 0xFFFFFFFFL
            z += (y shl 4) + c xor y + sum xor (y ushr 5) + d
            z = z and 0xFFFFFFFFL
        }

        // 最后，我们输出密文，因为我用的long，所以需要强制转换一下变成int
        bas.reset()
        writeInt(y.toInt())
        writeInt(z.toInt())
        return bas.toByteArray()
    }
    /**
     * 解密从offset开始的8字节密文
     *
     * @param in     密文字节数组
     * @param offset 密文开始位置
     * @return 明文
     */
    /**
     * 解密
     *
     * @param in 密文
     * @return 明文
     */
    private fun decipher(`in`: ByteArray, offset: Int = 0): ByteArray {
        // 迭代次数，16次
        var loop = 0x10
        // 得到密文和密钥的各个部分，注意java没有无符号类型，所以为了表示一个无符号的整数
        // 我们用了long，这个long的前32位是全0的，我们通过这种方式模拟无符号整数，后面用到的long也都是一样的
        // 而且为了保证前32位为0，需要和0xFFFFFFFF做一下位与
        var y = getUnsignedInt(`in`, offset, 4)
        var z = getUnsignedInt(`in`, offset + 4, 4)
        val a = getUnsignedInt(key, 0, 4)
        val b = getUnsignedInt(key, 4, 4)
        val c = getUnsignedInt(key, 8, 4)
        val d = getUnsignedInt(key, 12, 4)
        // 算法的一些控制变量，sum在这里也有数了，这个sum和迭代次数有关系
        // 因为delta是这么多，所以sum如果是这么多的话，迭代的时候减减减，减16次，最后
        // 得到0。反正这就是为了得到和加密时相反顺序的控制变量，这样才能解密呀～～
        var sum: Long = -0x1c886470
        sum = sum and 0xFFFFFFFFL
        var delta: Long = -0x61c88647
        delta = delta and 0xFFFFFFFFL

        // 迭代开始了， @_@
        while (loop-- > 0) {
            z -= (y shl 4) + c xor y + sum xor (y ushr 5) + d
            z = z and 0xFFFFFFFFL
            y -= (z shl 4) + a xor z + sum xor (z ushr 5) + b
            y = y and 0xFFFFFFFFL
            sum -= delta
            sum = sum and 0xFFFFFFFFL
        }
        bas.reset()
        writeInt(y.toInt())
        writeInt(z.toInt())
        return bas.toByteArray()
    }

    /**
     * 写入一个整型到输出流，高字节优先
     */
    private fun writeInt(t: Int) {
        bas.write(t ushr 24)
        bas.write(t ushr 16)
        bas.write(t ushr 8)
        bas.write(t)
    }

    /**
     * 加密8字节
     */
    private fun encrypt8Bytes() {
        // 这部分完成我上面所说的 plain ^ preCrypt，注意这里判断了是不是第一个8字节块，如果是的话，那个prePlain就当作preCrypt用
        pos = 0
        while (pos < 8) {
            if (header) plain[pos] = (plain[pos].toInt() xor prePlain[pos].toInt()).toByte() else plain[pos] =
                (plain[pos].toInt() xor out[preCrypt + pos].toInt()).toByte()
            pos++
        }
        // 这个完成我上面说的 f(plain ^ preCrypt)
        val crypted = encipher(plain)
        // 这个没什么，就是拷贝一下，java不像c，所以我只好这么干，c就不用这一步了
        System.arraycopy(crypted, 0, out, crypt, 8)

        // 这个完成了 f(plain ^ preCrypt) ^ prePlain，ok，下面拷贝一下就行了
        pos = 0
        while (pos < 8) {
            out[crypt + pos] = (out[crypt + pos].toInt() xor prePlain[pos].toInt()).toByte()
            pos++
        }
        System.arraycopy(plain, 0, prePlain, 0, 8)

        // 完成了加密，现在是调整crypt，preCrypt等等东西的时候了
        preCrypt = crypt
        crypt += 8
        pos = 0
        header = false
    }

    /**
     * 解密8个字节
     *
     * @param in     密文字节数组
     * @param offset 从何处开始解密
     * @param len    密文的长度
     * @return true表示解密成功
     */
    private fun decrypt8Bytes(`in`: ByteArray, offset: Int, len: Int) {
        // 这里第一步就是判断后面还有没有数据，没有就返回，如果有，就执行 crypt ^ prePlain
        pos = 0
        while (pos < 8) {
            if (contextStart + pos >= len) return
            prePlain[pos] = (prePlain[pos].toInt() xor `in`[offset + crypt + pos].toInt()).toByte()
            pos++
        }

        // 好，这里执行到了 d(crypt ^ prePlain)
        prePlain = decipher(prePlain)

        // 解密完成，最后一步好像没做？
        // 这里最后一步放到decrypt里面去做了，因为解密的步骤有点不太一样
        // 调整这些变量的值先
        contextStart += 8
        crypt += 8
        pos = 0
    }

    /**
     * 这是个随机因子产生器，用来填充头部的，如果为了调试，可以用一个固定值
     * 随机因子可以使相同的明文每次加密出来的密文都不一样
     *
     * @return 随机因子
     */
    private fun rand(): Int {
        return random.nextInt()
    }

    companion object {
        // 随机数对象
        private val random = Random()
        fun getUnsignedInt(`in`: ByteArray, offset: Int, len: Int): Long {
            var ret = 0L
            val end: Int
            end = if (len > 8) offset + 8 else offset + len
            for (i in offset until end) {
                ret = ret shl 8
                ret = ret or (`in`[i].toInt() and 0xff).toLong()
            }
            return ret and 0xffffffffL or (ret ushr 32)
        }
    }
}