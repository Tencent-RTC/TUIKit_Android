package io.trtc.tuikit.chat.uikit.components.aiassistant.audio

/**
 * 打断用的 pre-roll 缓冲：有界、溢出丢最旧、线程安全。
 *
 * ## 它解决的问题
 *
 * 播放态（AI 在外放 TTS）原先完全不编码也不上行，只用 VAD 高门限判打断。于是从用户开口到
 * 打断判定成立的这段音频**从来没有送给过 ASR**，打断之后开的又是一个全新会话——用户那句话
 * 一个字都不会出现，现象是「打断成功了，屏幕直接跳到正在听，说的内容全丢」。
 *
 * 这里把播放态每一帧的 Opus packet 先攒起来，打断后交给新会话在 `onStarted()` 之后补推，
 * 实时音频接在它后面。
 *
 * ## 为什么不是「播放期间一直开着 ASR 连续识别」
 *
 * 真机上已经观察到 ASR 会对环境噪声凭空返回文本（没人说话时吐出过
 * 「各位。」「嗯。」）。连续识别会把 AI 自己的回声也喂进去，很可能变成 AI 自己打断自己，
 * 比丢字更糟。pre-roll 保留了「能量门限」这道回声免疫的闸门（实测残留回声的越线帧数恒为
 * 0/25），同时把丢掉的话捞回来。
 *
 * ## 线程契约
 *
 * **这是本特性里唯一一个真正被两条线程同时碰的容器**：采集线程（40ms 一个 packet）写，主线程
 * （[drain] / [clear]）读。所以每个方法都加锁，而不是像 `AICallController` 里别的字段那样
 * 靠 `@Volatile` + 单向所有权——`@Volatile` 只保证单个引用的可见性，保证不了 ArrayDeque
 * 内部结构的一致性，边读边写会读到半截状态甚至抛异常。
 *
 * 锁是非竞争的（两条线程各自访问都是纳秒级），采集线程不会因此被拖慢。
 *
 * packet 本身是 VoiceAI SDK 每次回调新分配的 ByteArray，交出去之后 SDK 不会再碰，
 * 所以不必再拷贝一份。
 */
class PreRollBuffer(private val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive: $capacity" }
    }

    /**
     * 写全限定名是必要的，别"顺手简化"成默认导入的 `kotlin.collections.ArrayDeque`：本模块的
     * 编译器是 1.9.0（`settings.gradle.kts` 里钉死的），而依赖解析出来的 stdlib 是 1.9.24，
     * 这个版本差之下 `packets.addLast(...)` 会直接编译不过（Unresolved reference: addLast）——
     * 方法在 1.9.24 的字节码里是有的，是元数据不给 1.9.0 的编译器用。`java.util` 这个不受影响。
     */
    private val packets = java.util.ArrayDeque<ByteArray>()

    /** 仅供日志观测。 */
    val size: Int
        @Synchronized get() = packets.size

    /**
     * 无条件追加一帧，超出 [capacity] 丢最旧的。
     *
     * 用在「打断已判定、新 ASR 会话还没起来」的那段窗口上：用户此刻明确正在说话，不需要
     * 再由说话累积来门控——而且那时 VAD 刚被 `reset()` 过，按累积门控会立刻把缓冲清空。
     */
    @Synchronized
    fun append(packet: ByteArray) {
        while (packets.size >= capacity) {
            packets.removeFirst()
        }
        packets.addLast(packet)
    }

    /**
     * 把缓冲的起点对齐到人声起点：[speechAccumulationMs] 为 0 说明还没有人声起点（或者宽限
     * 已经过期、上一段说话作废了），此刻清空；大于 0 才追加。
     *
     * 这一条是 pre-roll 的安全底线，不只是省内存：不做对齐的话，缓冲里装的是打断前最后 2 秒
     * 的麦克风输入，而那 2 秒里绝大部分是 AI 外放漏回来的回声。把它补推给新会话，等于绕开了
     * 能量门限这道闸门，直接把 AI 自己的声音喂给 ASR。
     *
     * 用在播放态的每一帧上。
     */
    @Synchronized
    fun appendFromSpeechOnset(packet: ByteArray, speechAccumulationMs: Int) {
        if (speechAccumulationMs <= 0) {
            packets.clear()
        } else {
            append(packet)
        }
    }

    @Synchronized
    fun clear() {
        packets.clear()
    }

    /** 取走全部内容并清空，保持先进先出的时间顺序。 */
    @Synchronized
    fun drain(): List<ByteArray> {
        if (packets.isEmpty()) {
            return emptyList()
        }
        val drained = ArrayList(packets)
        packets.clear()
        return drained
    }
}
