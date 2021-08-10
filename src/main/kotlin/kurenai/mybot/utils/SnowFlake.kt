package kurenai.mybot.utils

/**
 *   SnowFlake算法
 */

class SnowFlake(
    private val dataCenterId: Long    // 数据中心
    , private val machineId: Long     // 机器标识
) {
    private var sequence = 0L   // 序列号
    private var lastStmp = -1L  // 上一次时间戳

    private val nextMill: Long
        get() {
            var mill = newstmp
            while (mill <= lastStmp) {
                mill = newstmp
            }
            return mill
        }

    private val newstmp: Long
        get() = System.currentTimeMillis()

    init {
        if (dataCenterId > MAX_DATA_CENTER_NUM || dataCenterId < 0) {
            throw IllegalArgumentException("dataCenterId can't be greater than MAX_DATA_CENTER_NUM or less than 0")
        }
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw IllegalArgumentException("machineId can't be greater than MAX_MACHINE_NUM or less than 0")
        }
    }

    /**
     * 产生下一个ID
     */
    @Synchronized
    fun nextId(): Long {
        var currStmp = newstmp
        if (currStmp < lastStmp) {
            throw RuntimeException("Clock moved backwards.  Refusing to generate id")
        }

        if (currStmp == lastStmp) {
            // 相同毫秒内，序列号自增
            sequence = sequence + 1 and MAX_SEQUENCE
            // 同一毫秒的序列数已经达到最大
            if (sequence == 0L) {
                currStmp = nextMill
            }
        } else {
            // 不同毫秒内，序列号置为0
            sequence = 0L
        }

        lastStmp = currStmp

        return (currStmp - START_STMP shl TIMESTMP_LEFT      // 时间戳部分
                or (dataCenterId shl DATA_CENTER_LEFT)       // 数据中心部分
                or (machineId shl MACHINE_LEFT)              // 机器标识部分
                or sequence)                                 // 序列号部分
    }

    companion object {
        /**
         * 起始的时间戳
         */
        private const val START_STMP = 1480166465631L

        /**
         * 每一部分占用的位数
         */
        private const val SEQUENCE_BIT: Int = 12    // 序列号占用的位数
        private const val MACHINE_BIT: Int = 5      // 机器标识占用的位数
        private const val DATA_CENTER_BIT: Int = 5  // 数据中心占用的位数

        /**
         * 每一部分的最大值
         */
        private const val MAX_DATA_CENTER_NUM = (-1L shl DATA_CENTER_BIT).inv()
        private const val MAX_MACHINE_NUM = (-1L shl MACHINE_BIT).inv()
        private const val MAX_SEQUENCE = (-1L shl SEQUENCE_BIT).inv()

        /**
         * 每一部分向左的位移
         */
        private const val MACHINE_LEFT = SEQUENCE_BIT
        private const val DATA_CENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT
        private const val TIMESTMP_LEFT = DATA_CENTER_LEFT + DATA_CENTER_BIT

        @JvmStatic
        fun main(args: Array<String>) {
            val snowFlake = SnowFlake(2, 3)

            for (i in 0 until (1 shl 12)) {
                println(snowFlake.nextId())
            }
        }
    }
}